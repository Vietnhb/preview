# Bảo trì backend PhysLive

## Kết quả

So sánh Java runtime với HEAD trước thay đổi, không tính test, frontend và tài liệu:

- 260 → 234 file Java, kể cả chức năng bình luận/lượt thích mới và client tích hợp AI.
- 15.009 → 14.167 dòng Java, giảm 842 dòng.
- Thư mục nhóm theo nghiệp vụ dưới `system`, tách rõ tầng MVC trong từng nhóm; `base` chỉ giữ thành phần dùng chung và `integration` giữ client dịch vụ ngoài.
- 172 → 162 API theo cặp HTTP method + path: bỏ 11 endpoint không có luồng thực thi thành công, bỏ 3 endpoint trùng, thêm 4 endpoint tương tác.
- Bỏ dependency Redis và Rhino vì không còn consumer trong runtime.

[Danh mục API](api-catalog.md) mô tả từng API, nhiệm vụ và role qua lớp HTTP. Số API của các role có giao nhau và bao gồm endpoint public/hồ sơ; service vẫn kiểm tra quyền nghiệp vụ và phạm vi tài nguyên.

## Cấu trúc

Áp dụng MVC cho REST: controller nhận HTTP, validation, multipart và định dạng response; service giữ nghiệp vụ, transaction và quyền tài nguyên; repository truy cập dữ liệu; entity mô tả persistence; DTO tạo hợp đồng JSON của view. Controller không gọi repository/EntityManager, không đưa entity ra response và không lấy DTO từ lớp service.

Cấu trúc tham khảo `C:/Users/kemin/Downloads/StarterProject202603/backend`: nhóm nghiệp vụ trong `system`, phần dùng chung trong `base`. PhysLive tách tiếp các tầng bên trong mỗi nghiệp vụ:

```text
com/example/backend/
├── Application.java
├── base/
│   ├── crud/
│   │   ├── dto/PageResponse.java
│   │   └── model/
│   │       ├── entity/AuditedEntity.java
│   │       └── enums/LifecycleStatus.java
│   └── web/
│       ├── controller/GlobalExceptionHandler.java
│       └── dto/ErrorResponse.java
├── system/
│   ├── account/
│   ├── assignment/
│   ├── curriculum/
│   ├── library/
│   ├── operations/
│   ├── physics/
│   ├── problem/
│   ├── realtime/
│   ├── reviewer/
│   ├── school/
│   │   └── dataio/
│   ├── simulation/
│   │   └── dataio/
│   └── support/
├── integration/
│   └── ai/AIClient.java
├── exception/
├── config/
├── security/
└── bootstrap/
```

- `system/<nghiệp vụ>/controller`: route HTTP, danh tính từ request, validation, multipart, status và response. Không truy cập repository/EntityManager hoặc trả JPA entity. Adapter HTTP/SSE thuộc `system/realtime/controller`.
- `system/<nghiệp vụ>/service`: nghiệp vụ, transaction, quyền đối với tài nguyên và phạm vi trường; gồm điều phối AI, mô phỏng và thanh toán.
- `system/<nghiệp vụ>/repository`: JPA, native query và thao tác persistence. Không xử lý HTTP hoặc quyết định quyền người dùng.
- `system/<nghiệp vụ>/model/entity`: bảng, quan hệ và khóa của database. `model/enums` chứa kiểu trạng thái/role của mô hình nghiệp vụ.
- `system/<nghiệp vụ>/dto`: request/response của REST view, gồm validation của dữ liệu đầu vào. Nhóm DTO nhỏ cùng nghiệp vụ trong các file contracts; không khai báo hợp đồng JSON bên trong service.
- `system/<nghiệp vụ>/mapper`: chuyển entity/projection sang DTO; không truy cập database hoặc kiểm tra quyền.
- `base/crud`: `PageResponse`, `AuditedEntity`, `LifecycleStatus` dùng chung. Không sao chép generic CRUD, filter, mapper kế thừa hoặc controller cơ sở của template khi không có nhu cầu.
- `base/web`: HTTP handler lỗi và DTO lỗi dùng chung.
- `system/school/dataio`: controller nhập/tải mẫu CSV, service kiểm tra/nhập dữ liệu, `SchoolCsvParser` và DTO kết quả nhập.
- `system/simulation/dataio`: controller/service xuất dữ liệu mô phỏng.
- `integration/ai`: client giao tiếp HTTP/JSON với provider AI, OCR và Jev.
- `exception`: bảy loại lỗi dùng chung; không chứa HTTP handler.
- `config`: toàn bộ lớp `*Config` và `*Properties`, gồm database, OpenAPI, client ngoài, HTTP role gates và properties được ánh xạ từ ENV.
- `security`: `JwtUtil`, `JwtFilter`, `AccountCapabilityInterceptor` và `PasswordPolicy`.
- `bootstrap`: khởi tạo chương trình và schema khi được bật rõ ràng.

Luồng phụ thuộc chính trong từng nghiệp vụ là `controller → service → repository → model/entity`; service dùng mapper để tạo DTO trả cho controller. DTO là dữ liệu cho view REST, không thay thế entity persistence. Cấu hình và bảo mật hỗ trợ các tầng này. Chỉ tạo thư mục tầng có lớp thực tế, không tạo sẵn thư mục rỗng cho mọi nghiệp vụ.

Tên lớp giữ tên nghiệp vụ như `UserController`, `LibraryService`, `SchoolClassRepository` để dễ tìm. Service chỉ có một triển khai không cần thêm interface/implementation. Khi thêm chức năng, đặt lớp trong đúng nghiệp vụ và tầng, dùng service để kiểm tra quyền tài nguyên trước khi truy cập repository. `base` chỉ nhận thành phần đang được nhiều nghiệp vụ dùng; code riêng của một nghiệp vụ ở lại trong `system`.

Ví dụ chức năng lớp học nằm trong `system/school`: `controller/SchoolClassController.java` nhận request, `service/SchoolClassService.java` kiểm tra quyền và xử lý nghiệp vụ, `repository/SchoolClassRepository.java` truy cập `model/entity/SchoolClass.java`, còn `dto/SchoolClassContracts.java` giữ hợp đồng JSON. DTO sự kiện realtime nằm tại `system/realtime/dto/RealtimeEvent.java`; `SseEmitter` chỉ nằm trong adapter ở `system/realtime/controller`.

Trong template, `dataio` là nhập/xuất dữ liệu dạng file, có parser/importer/exporter cho CSV, Excel và PDF. PhysLive dùng tên này cho nhập CSV trường học và xuất mô phỏng. Payload gửi AI nằm trong luồng nghiệp vụ mô phỏng và client tích hợp AI; không chuyển nó vào `dataio`.

## AI và cấu hình

`system/simulation/controller/AIController` xử lý validation/multipart rồi chuyển dữ liệu tới `system/simulation/service/AIService`; service không nhận MultipartFile hoặc DTO được khai báo trong controller. `integration/ai/AIClient` xử lý HTTP, JSON và giao thức provider/OCR/Jev; `AIService` giữ điều phối nghiệp vụ, chữ ký kế hoạch, xác minh schema và solver. `AIProperties` gom cấu hình provider/visual thành kiểu dữ liệu có kiểm tra URL, thời gian chờ và giới hạn. Kế hoạch có chữ ký, xác minh schema, tính vật lý và phát lại dùng hợp đồng hiện tại.

`AIService` còn 762 dòng, so với 1.026 dòng trong bản gốc được cung cấp. `AIClient` có 295 dòng, tách riêng giao thức tích hợp để có thể thay provider mà không phải sửa phần điều phối nghiệp vụ. Phần này là tách trách nhiệm; thống kê giảm toàn backend được ghi ở đầu tài liệu.

`backend/.env.example` là bản mẫu có chú thích từng khu. `backend/.env.local` là giá trị local, bị Git bỏ qua. `application.properties` ánh xạ các biến sang cấu hình Spring; cấu hình hạ tầng cố định vẫn nằm ở đây.

Các khu ENV gồm ứng dụng/migration, PostgreSQL/pool, JWT/CORS, upload, AI hiểu văn bản/OCR, AI hiển thị, Jev, VNPay, cộng đồng và runtime mô phỏng. Các biến điều khiển cũ không còn consumer đã được bỏ; thông tin kết nối và khóa đang dùng được giữ nguyên. Các khóa Supabase cho tích hợp ngoài vẫn được giữ riêng trong ENV local.

- `DB_FETCH_BATCH_SIZE=64`: tải quan hệ lazy theo nhóm; giảm truy vấn lặp trong mục lục/thư viện.
- `COMMUNITY_COMMENT_MAX_CHARACTERS=2000`: giới hạn bình luận; API trả `commentMaxLength` để FE dùng cùng giá trị.
- `COMMUNITY_MAX_PAGE_SIZE=50`: giới hạn số bình luận mỗi trang.
- `AI_VISUAL_PROVIDER`: `gemini_interactions` dùng giao thức Gemini; các tên tương thích chat completions, gồm `groq`, tiếp tục dùng endpoint tương thích đã cấu hình. Tên provider mới không cần thêm vào danh sách vendor trong code.
- `BOOTSTRAP_CATALOGS_ENABLED`: chỉ bật khi chủ động khởi tạo/cập nhật dữ liệu tham chiếu. Lần kiểm tra runtime này đặt `false`.

Giới hạn cấu trúc/kiểm tra bảo mật như định dạng CSV, role hợp lệ và quy tắc nghiệp vụ tiếp tục nằm trong code; không dùng ENV để thay thế kiểm tra quyền.

## Tương tác cộng đồng

Migration `V39__community_discussion.sql` đã áp dụng thành công vào database cấu hình local. Migration chỉ bổ sung `library_comments`, `library_likes` và index; không thay đổi tài khoản hoặc role.

- Khách chỉ đọc tài nguyên PUBLIC đang hoạt động, đã APPROVED/FEATURED và bình luận của tài nguyên đó; không có thông báo dành cho khách trên UI.
- Tài khoản đăng nhập được bình luận/thích trong phạm vi được phép đọc; ADMIN bị giới hạn không được ghi tương tác.
- Tài nguyên SHARED phải thuộc trường của người đọc. Tài nguyên riêng, chờ duyệt hoặc đã gỡ không mở được qua API tương tác.
- Chủ bình luận có thể xóa; MANAGER hoặc moderator đúng phạm vi/capability có thể xóa theo quyền.
- Lượt thích có khóa duy nhất `(item_id,user_id)`, dùng `ON CONFLICT DO NOTHING`; PUT trạng thái lặp lại không tạo lượt trùng.
- Mutation khóa bản ghi tài nguyên trong transaction; kiểm tra quyền và trạng thái trước khi ghi.
- Bình luận được hiển thị như văn bản, không render HTML người dùng nhập. Request của dialog được hủy khi đóng/chuyển tài nguyên.

Bốn API mới dùng chung `LibraryController`, một service và một file hợp đồng DTO gồm các record. Lượt thích dùng native query tại repository hiện có, không tạo thêm entity/service/controller riêng.

## Ranh giới MVC và database

- PasswordEncoder và kiểm tra bản quyền thuộc UserService; UserController chỉ lấy danh tính từ HTTP rồi gọi service.
- Kiểm tra phạm vi trường nằm trong service theo schoolId của đường dẫn; request body không thể đổi sang trường khác.
- Lọc schema theo capability và chọn chương trình theo schema đã duyệt nằm trong service.
- School, license plan, schema, solver và nhật ký học sinh trả DTO tách khỏi JPA entity, giữ các trường JSON đang dùng.
- DTO phân trang/báo cáo không nằm trong service; DTO nhỏ cùng nghiệp vụ được gộp trong các nhóm contracts.
- ApiException tập trung ánh xạ loại lỗi; service dùng factory lỗi thay vì tự lựa chọn HttpStatus.
- ApiCatalogTest quét bytecode để kiểm tra tầng trong package nghiệp vụ, controller không truy cập repository/EntityManager, service không gọi controller/HTTP servlet, repository/model/DTO/mapper không gọi tầng trên, và HTTP contract chỉ dùng DTO/enum. Kiểm tra này chạy trong CI cùng các test phân quyền.

Audit database thật xác định 38 bảng đều còn dùng bởi runtime, native query, collection hoặc lịch sử. Không gộp bảng quan hệ có vòng đời khác nhau chỉ để giảm số bảng. Migration V40 đã áp dụng thành công vào database cấu hình local và bỏ ba cột không có reader/writer/dependency và hoàn toàn rỗng: `extraction_runs.raw_response` (193 bản ghi), `specifications.clarification_conversation` (119), `users.deactivation_reason` (17). Guard chặn migration nếu một database khác có dữ liệu trong các cột này. Không sửa migration đã áp dụng; không xóa tài khoản hay lịch sử mô phỏng.

`schools.next_plan_code` còn một lựa chọn nâng gói cũ nên giữ dữ liệu; API next-plan không có luồng thành công đã bỏ. Quy tắc nâng gói ngay và chặn hạ gói đang hoạt động vẫn dùng checkout/purchase. Khóa @Version, staff_alias và provenance của các lần chạy được giữ.

## Những phần đã bỏ

- Sáu mutation của `/api/problems` và hai mutation thực thi `/api/simulations` cũ: các handler chỉ trả HTTP 410. Luồng `/api/simulation` và các API đọc/phát lại được giữ.
- POST `/api/evaluations/run` chỉ trả 410, hơn 400 dòng helper không thể chạy tới và nút FE gọi luồng này; API đọc/so sánh lịch sử đánh giá và bộ đề/gán nhãn/phân xử vẫn giữ.
- POST `/api/auth/signup` luôn từ chối; đăng ký trường dùng checkout thanh toán. PUT `/api/school/billing/next-plan` luôn từ chối; helper FE không có consumer đã bỏ.
- Alias `/api/user/all`: danh sách người dùng dùng `/api/admin/users`; helper FE không có consumer đã bỏ.
- POST `/api/reviewer/schemas` và PUT `/api/reviewer/schemas/{schemaId}/lifecycle`: dùng API `/api/schemas` tương ứng. FE tạo schema đã đổi sang đường chính thức; API phiên bản theo ID vẫn giữ.
- `EmbeddingUnavailableException`, `ExtractionRunRepository`, `SchoolService` không có consumer và bốn DTO chỉ dùng bởi handler đã ngừng hoạt động.
- Helper schema snapshot/historical alias/projectCoreTypes, readiness.toJson và simulation.latestFor không có caller; các helper phát lại đang dùng vẫn giữ.
- Hai prompt đặc tả cũ, matcher/interceptor cho route đã bỏ, kiểm tra template trỏ vào catalog đã không còn được sinh.
- CI trỏ vào script/service đã bị xóa được đồng bộ sang build/test hiện tại. `npm run check:templates` dùng validator nguồn schema chung.

Entity và API đọc dữ liệu mô phỏng/đề bài cũ vẫn cần cho phát lại; không phải dead code.

## Kiểm tra

Sau khi nhóm lại thư mục theo template và tách `AIClient`:

- Maven clean compile rồi package thành công: 293 test được thu thập, 291 đạt, không có test lỗi hoặc thất bại; hai test Testcontainers bỏ qua vì Docker không sẵn sàng. Các test HTTP/security, ranh giới MVC, scope trường, role, CSV, mật khẩu, schema và AI transport đều đạt.
- Danh mục API được tạo lại; so sánh HTTP method, path và role trước/sau nhóm thư mục khớp toàn bộ 162 API.
- Kiểm tra bytecode chạy trên bản build sạch, không giữ package cũ. Hợp đồng DTO không chứa mật khẩu và hạn chế đổi mật khẩu lần đầu được xác minh bằng test tự động.
- Executable jar sau khi nhóm lại thư mục khởi động thành công tại cổng 8080, health `UP`; Flyway xác nhận schema ở phiên bản 40 và JPA `validate` thành công. API chương trình học và danh sách gói trả 200 cho khách. Kết nối PostgreSQL timeout ở lần khởi động đầu rồi thành công khi kết nối lại.

Các kết quả chức năng đã xác minh trong đợt trước vẫn được lưu làm lịch sử; frontend, browser và migration database không được chạy lại chỉ cho thay đổi thư mục:

- `npm run check`: architecture, validator schema, ESLint, 93 test và production build đạt.
- PostgreSQL thật: V39, like/unlike lặp và cascade; V40 chặn riêng từng cột khi có dữ liệu, bỏ đúng ba cột khi rỗng và giữ cột/dữ liệu khác. Các phép kiểm tra dùng schema riêng và rollback toàn bộ.
- Browser fixture: GUEST chỉ đọc, STUDENT đăng/xóa bình luận và thích/bỏ thích, tải thêm; desktop 1440 px và mobile 390 px. Fixture không ghi vào database.
- GET cộng đồng, cây chương trình và danh sách gói cho khách trả 200; POST bình luận của khách trả 401. Không tạo/sửa tài khoản thật để chạy QA. Database sau migration còn đủ 17 user và 6 role đúng ID/in hoa.
- Một lần đo mục lục trên cùng database: khoảng 16,7 giây trước batch fetch và 1,2 giây sau. Đây là phép đo local, không phải benchmark tải đồng thời.

Tạo lại thống kê API: chạy `./mvnw -Dtest=ApiCatalogTest test` trong `backend`, sau đó `node scripts/api-catalog.mjs` tại repository. Danh mục dựa trên mapping đã biên dịch; không đếm bằng chuỗi grep hoặc số controller.
