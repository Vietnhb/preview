# PhysLive backend maintainability refactor report

## Phạm vi đã xác minh và triển khai

- `SecurityConfig` là nơi duy nhất giữ policy HTTP. Đã bổ sung matcher cho `/api/evaluations` và `/api/evaluations/**` với `REVIEWER`/`ADMIN`, giới hạn `POST /api/assignments` cho `TEACHER`/`ADMIN`, và làm rõ cả route gốc `/api/support/admin` cho `ADMIN`.
- `ReviewerVersionsController` đã được làm mỏng. Lifecycle, duplicate/version identity, installed solver validation, binding checksum và repository transaction chuyển sang `ReviewerVersionService`.
- `AdminOperationsController` đã được làm mỏng. Plan/school validation, lifecycle data, validation-run mapping và repository transaction chuyển sang `AdminOperationsService`.
- `ProblemService.extract` không còn giữ transaction trong lúc gọi AI. Transaction đầu tiên lưu extraction run ở `RUNNING`; AI nhận text snapshot; transaction sau ghi specification thành công hoặc ghi `FAILED` riêng.
- `EvaluationService.run` không còn giữ transaction trong lúc chạy extraction trên corpus. Run được persist ở `RUNNING` trước; corpus snapshot bất biến được giữ trong bộ nhớ và trong `configuration`; kết quả cuối cùng ghi `COMPLETED`, lỗi ghi `FAILED` trong transaction khác. Configuration cuối lưu corpus snapshot, schema version/definition và model version thực tế đã dùng.
- `SchemaDefinitionService` có thêm `approvedDefinitionSnapshot`, trả về boundary value gồm schema id/version/definition để không mang managed entity ra tác vụ chậm.
- `SupportService` giữ cấu trúc một service, nhưng đã bổ sung giới hạn subject 180 ký tự theo column, giới hạn payload 10.000 ký tự, validate kind/request, và làm rõ phản hồi admin chuyển `OPEN` thành `READ` khi chưa chọn trạng thái khác.
- Validation đầu vào của request được tập trung trong các DTO thuộc `dto/...`; controller dùng `@Valid`, còn service không còn chứa `jakarta.validation.constraints`. Service vẫn giữ kiểm tra phòng thủ và kiểm tra nghiệp vụ cho các luồng gọi trực tiếp ngoài HTTP.

## Contract và hành vi được giữ nguyên

- URL, HTTP method, request fields và response shape hiện tại không đổi.
- Chuẩn hóa role reviewer thành `REVIEWER`; `V25__rename_content_reviewer_role.sql` chuyển dữ liệu legacy và giữ nguyên tài khoản hiện có.
- `JwtFilter` chỉ chấp nhận tài khoản có `active = true`; `V26__backfill_legacy_user_active_flags.sql` và `V27__lock_remaining_null_user_active_flags.sql` xử lý dữ liệu legacy, còn runtime vẫn khóa mọi giá trị khác `true`.
- Login và JWT đều thông báo rõ tài khoản bị khóa khi `active` là `NULL` hoặc `false`; `V27__lock_remaining_null_user_active_flags.sql` khóa các dòng legacy còn sót lại.
- Không đổi schema/version identity, checksum, solver numerical/reference, simulation persistence, replay hoặc output contract.
- Không sửa migration đã áp dụng; thêm các migration mới `V24`–`V27` cho catalog gói, chuẩn hóa role reviewer và trạng thái tài khoản.
- Cập nhật frontend role contract sang `REVIEWER`, không đổi API shape.

## Lỗi nghiệp vụ/bảo mật đã sửa

Trước thay đổi, evaluations chỉ rơi vào `authenticated()` và `POST /api/assignments` nằm trong matcher tổng quát cho phép `STUDENT`. Cả hai đều đã được sửa bằng matcher method/path cụ thể đứng trước fallback. Route `/api/support/admin` cũng được khai báo tường minh để không phụ thuộc cách matcher xử lý suffix `/**`.

Các kiểm tra ownership, school/class/student relationship và lifecycle trong service vẫn được giữ nguyên; việc đưa role vào `SecurityConfig` không thay thế kiểm tra phạm vi dữ liệu.

## Test và bằng chứng

- `./mvnw.cmd -q -Dtest=SecurityConfigAuthorizationTest test`: đạt 3 test; gồm unauthenticated `401`, sai role `403`, đúng role vượt filter chain; kiểm tra evaluations, assignment creation, support admin và xác nhận request bị từ chối không gọi probe handler.
- `./mvnw.cmd -q -Dtest=SupportServiceTest test`: đạt 3 test cho trim/length/status transition của support.
- `./mvnw.cmd -q '-Dtest=SupportServiceTest,SecurityConfigAuthorizationTest' test`: đạt.
- `./mvnw.cmd -q '-Dtest=FreshSchemaBootstrapMigrationTest' test`: đạt; xác nhận database mới áp dụng đầy đủ migration đến version `25` và seed được catalog gói mặc định.
- Compile sau refactor: `./mvnw.cmd -q -DskipTests compile` đạt.

## Giới hạn chưa kiểm chứng

- Các test integration cần PostgreSQL/Testcontainers chưa chạy do Docker không khả dụng; vì vậy chưa tuyên bố đã xác minh runtime transaction trên DB thật.
- Chưa gọi AI/OCR API thật. Luồng AI được tách transaction bằng code inspection và compile/test hiện có; chưa có integration test provider failure kiểm tra trực tiếp trạng thái `FAILED` sau commit.
- Các controller nghiệp vụ chính trong audit hiện không còn truy vấn repository trực tiếp; school-management, auth-plan lookup, student action log và reviewer module đã gọi service. Các lớp service vẫn giữ response shape cũ để không mở rộng contract.
- Các list cũ của admin/reviewer vẫn giữ response dạng array để bảo toàn frontend contract; pagination chỉ nên triển khai cùng consumer change khi có dữ liệu vận hành chứng minh cần thiết.

## Files mới

- `backend/src/main/java/com/example/backend/service/reviewer/ReviewerVersionService.java`: use case cho reviewer schema/solver version.
- `backend/src/main/java/com/example/backend/service/admin/AdminOperationsService.java`: use case cho admin plans, schools và validation runs.
- `backend/src/main/java/com/example/backend/service/assignment/StudentActionLogService.java`: ownership check và persistence cho student action log.
- `backend/src/main/resources/db/migration/V24__seed_default_license_plans.sql`: seed idempotent các gói đăng ký mặc định.
- `backend/src/main/resources/db/migration/V25__rename_content_reviewer_role.sql`: đổi role legacy sang `REVIEWER` mà không mất tài khoản.
- `backend/src/test/java/com/example/backend/security/SecurityConfigAuthorizationTest.java`: regression HTTP authorization qua `SecurityFilterChain` thật.
