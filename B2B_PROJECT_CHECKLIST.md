# Checklist triển khai PhysLive B2B

**Cập nhật:** 19/09/2026  
**Phạm vi đối chiếu:** `B2B_SYSTEM_DESIGN.md`, `ROLES_FINAL.md`, `B2B_IMPLEMENTATION_SUMMARY.md` và mã nguồn hiện tại trong `backend/`, `react-client/`.  
**Mục đích:** theo dõi đúng trạng thái triển khai, không đánh dấu hoàn thành chỉ vì nội dung đã xuất hiện trong tài liệu.

## Cách đọc trạng thái

- [x] Đã có code và có đường kiểm tra tương ứng.
- [~] Có một phần code hoặc mới có model/API; chưa đủ luồng người dùng.
- [ ] Chưa triển khai.
- [!] Phụ thuộc cấu hình, migration hoặc kiểm thử môi trường thật.

## Quyết định sản phẩm đã chốt

- [x] Hệ thống có 5 vai trò: `ADMIN`, `REVIEWER`, `SCHOOL_MANAGER`, `TEACHER`, `STUDENT`.
- [x] Trường được tự đăng ký tài khoản `SCHOOL_MANAGER` và chọn gói.
- [x] Chỉ kích hoạt trường và tài khoản quản lý sau khi backend xác minh thanh toán VNPAY thành công.
- [x] Giáo viên và học sinh do quản lý trường tạo; endpoint đăng ký cá nhân cũ vẫn bị khóa.
- [x] Quota AI tính bằng token thực tế từ provider; một yêu cầu đang chạy được hoàn tất và ghi nhận đủ token, yêu cầu tiếp theo mới bị chặn.
- [x] Nâng gói áp dụng ngay, tính chênh lệch theo số ngày còn lại và giữ ngày hết hạn; hạ gói lưu cho kỳ kế tiếp.
- [x] Cơ sở dữ liệu mục tiêu là PostgreSQL trên Supabase.
- [!] VNPAY đang là Sandbox; chưa có merchant credentials và IPN URL công khai trong môi trường này.

> `ROLES_FINAL.md` vẫn còn phần workflow cũ mô tả admin duyệt trường và tạo `SCHOOL_MANAGER`. Phần này cần được hiểu theo quyết định mới ở trên; không dùng làm yêu cầu ngược lại cho luồng đăng ký hiện tại.

## 1. Nền tảng và dữ liệu B2B

- [x] Spring Boot + React/TypeScript + JWT được giữ lại từ dự án hiện tại.
- [x] User có liên kết `school`; role platform không gắn trường, role trường phải gắn trường.
- [x] Kiểm tra role-school consistency ở service và trigger PostgreSQL trong `data.sql`.
- [x] Giới hạn một `SCHOOL_MANAGER` đang hoạt động cho mỗi trường.
- [x] Soft delete/suspend/restore tài khoản; không xóa cứng qua giao diện quản trị.
- [x] License của trường có ngày bắt đầu, ngày hết hạn, trạng thái hoạt động và quota token tháng.
- [x] Có migration/seed cho `license_plans`, `school_payments` và quota học sinh.
- [!] Phải chạy `data.sql` (đã bao gồm bảng lớp, enrollment và phân công giáo viên) trên Supabase bằng SQL Editor hoặc kết nối direct trước khi dùng `JPA_DDL_AUTO=validate`.
- [ ] Chưa có migration versioning riêng (Flyway/Liquibase); hiện đang dựa vào script SQL và JPA update trong local.

## 2. Đăng ký trường, gói và thanh toán

### Đã làm

- [x] Trang `/signup` có 3 bước: chọn gói, nhập thông tin trường/tài khoản quản lý, xác nhận thanh toán.
- [x] Danh mục gói được đọc từ PostgreSQL, không hard-code giá ở giao diện.
- [x] Có ba gói seed theo thiết kế: Starter, Professional, Enterprise.
- [x] Tạo trường và tài khoản quản lý ở trạng thái chưa kích hoạt trước khi thanh toán.
- [x] Tạo giao dịch lưu snapshot giá, quota, mục đích và thời hạn để giá gói thay đổi không làm sai giao dịch cũ.
- [x] Tạo URL VNPAY với HMAC-SHA512, số tiền nhân 100, thời hạn giao dịch và return URL.
- [x] IPN kiểm tra chữ ký, merchant, mã giao dịch, số tiền và trạng thái thanh toán.
- [x] Callback trùng được khóa theo dòng giao dịch; không gia hạn lặp.
- [x] Return page `/signup/payment-result` chỉ đọc trạng thái server, không tin query string để tự kích hoạt.
- [x] Có luồng khôi phục thanh toán bỏ dở bằng email và mật khẩu quản lý.
- [x] Quản lý trường có `/school/billing`: xem license, quota học sinh, quota AI và lịch sử giao dịch.
- [x] Có báo giá nâng gói theo số ngày còn lại và lưu gói kỳ sau.
- [x] Admin có `/admin/plans` để sửa danh mục gói; giao dịch cũ giữ số tiền đã snapshot.
- [x] Admin có thông báo trong chuông khi giao dịch đã thanh toán hoặc cần đối soát.

### Còn thiếu hoặc cần xác minh

- [!] Chưa thử giao dịch VNPAY Sandbox thật.
- [!] Chưa đăng ký IPN URL trên merchant VNPAY; `localhost` không nhận được IPN từ VNPAY.
- [x] Có job định kỳ truy vấn `querydr`, endpoint admin đối soát thủ công và bảng giao dịch.
- [ ] Chưa có luồng hoàn tiền VNPAY.
- [ ] Chưa có hóa đơn điện tử, email xác nhận, email nhắc gia hạn hoặc thông báo ngoài hệ thống.
- [ ] Chưa có cơ chế tự động gia hạn theo gói kỳ sau; hiện chỉ lưu lựa chọn và yêu cầu tạo thanh toán.
- [ ] Chưa có xử lý retry/đối soát thân thiện cho mọi trạng thái VNPAY (`01`, `04`, `05`, `07`, `09`).
- [x] Admin có UI xem giao dịch, tổng doanh thu và retry đối soát giao dịch `REQUIRES_REVIEW`.

## 3. Phân quyền theo vai trò

### ADMIN — quản trị platform

- [x] Xem/quản lý người dùng platform và người dùng trường.
- [x] Tạo/sửa/suspend/restore tài khoản theo rule role-school.
- [x] Quản lý trường, license thủ công, quota và danh mục gói.
- [x] Quản lý curriculum và validation runs.
- [x] Được phép xem các khu vực reviewer theo authorization backend.
- [~] Dashboard có số liệu user/validation cơ bản.
- [x] Trang `Feedback` và `Messages` có danh sách backend, modal xem, phản hồi và đóng yêu cầu.
- [~] Có báo cáo doanh thu gắn với payment reconciliation; MRR/conversion theo cohort chưa có.

### REVIEWER — chuyên gia nội dung

- [x] Resolve ambiguity extraction.
- [x] Tạo và thay đổi lifecycle schema.
- [x] Có API cho solver/reference version và benchmark review ở backend.
- [~] Có màn hình reviewer cho một số queue/schema/benchmark; cần đối chiếu hết hành động với permission matrix.
- [x] Quy trình teacher submit → reviewer queue → approve/reject/remove/feature đã nối backend và UI.
- [~] Có audit trail cho mọi quyết định moderation; SLA dashboard/nhắc hạn reviewer chưa có.

### SCHOOL_MANAGER — quản lý một trường

- [x] Đăng nhập, quản lý giáo viên/học sinh trong trường.
- [x] Tạo/sửa/suspend/restore tài khoản thuộc trường.
- [x] Xem và quản lý gói, license, quota, giao dịch.
- [x] Bị chuyển sang read-only khi license hết hạn hoặc chưa hợp lệ.
- [x] Có entity/repository/controller/service/UI cho lớp, enrollment và phân công giáo viên; dữ liệu được giới hạn theo `schoolId`.
- [x] Quản lý có thể tạo, sửa, archive lớp; archive dùng trạng thái mềm và kết thúc enrollment đang hoạt động.
- [x] Có luồng phân giáo viên vào lớp và gỡ phân công.
- [x] Có import CSV giáo viên/học sinh và kết quả lỗi theo dòng.
- [x] Có báo cáo tổng hợp trường, danh sách lớp, export CSV và token audit.
- [x] Có thao tác chuyển học sinh giữa các lớp cùng năm học; enrollment cũ chuyển `TRANSFERRED`, enrollment mới thành `ACTIVE`.

### TEACHER — giáo viên

- [x] Tạo mô phỏng qua flow problem/extraction/specification hiện có.
- [x] Lưu simulation đã validate vào personal library.
- [x] Tạo assignment và xem danh sách assignment/submission.
- [x] Có màn hình làm việc assignment và xem bài nộp.
- [x] Chia sẻ library theo visibility và moderation cộng đồng đã nối đủ queue/reviewer actions.
- [x] Assignment kiểm tra giáo viên và học sinh cùng trường, học sinh có enrollment đang hoạt động và giáo viên được phân công vào lớp đó.
- [~] Có criteria đáp án số, tolerance, điểm tối đa và auto-grade; công thức nhiều bước còn cần mở rộng.
- [x] Teacher review/confirm, feedback và reopen cho làm lại đã có.
- [~] Có report theo assignment với assigned/submitted/pending/graded/confirmed/average; báo cáo phân rã theo lớp còn thiếu.

### STUDENT — học sinh

- [x] Xem assignment được gán.
- [x] Gửi prediction một lần cho assignment.
- [x] Xem simulation/replay snapshot sau khi gửi prediction.
- [x] Có UI assignment workspace và shared library cơ bản.
- [~] Action log được lưu qua endpoint và ghi các thao tác chính; realtime websocket chưa có.
- [~] Auto-grading đáp án số + tolerance đã có; solver-specific formula grading chưa có.
- [~] Assignment trả score/feedback/status và cho phép reopen; UI lịch sử nhiều lần chưa có.
- [x] Backend chặn submission mới khi còn submission chưa được reopen.

## 4. AI, mô phỏng và vật lý

- [x] Có pipeline nhập đề bài, nhập ảnh/OCR, extraction, ambiguity và specification.
- [x] Có validation readiness và physics validation.
- [x] Có solver/reference solver cho Kinematics, Dynamics và Circuit.
- [x] Có lưu simulation run và snapshot để assignment dùng đúng phiên bản.
- [x] AI usage lấy `usage.total_tokens`; thiếu usage hoặc provider lỗi không tự bịa số token.
- [x] Quota được serialize theo trường và reset theo tháng.
- [x] Học sinh chạy simulation không trừ quota tạo AI.
- [~] Có rule-based extraction fallback, nhưng chất lượng AI provider/OCR thật chưa được benchmark trong môi trường hiện tại.
- [x] Có token audit theo ngày, operation, user, school license và UI trong báo cáo trường.
- [ ] Chưa có mua thêm token add-on.
- [ ] Chưa có cache/ước lượng chi phí provider và cảnh báo chi tiết theo ngưỡng.

## 5. Library và moderation

- [x] Personal library, folder, rename, move và soft remove.
- [x] Chỉ cho lưu simulation đã validate.
- [x] Shared visibility có giới hạn theo school scope hiện tại.
- [~] Reviewer có các API nội dung/schema liên quan.
- [x] Có queue duyệt shared simulation theo moderation status.
- [x] Reviewer UI có approve/feature/reject/remove.
- [x] Teacher có API clone shared simulation sang folder cá nhân để customize tiếp.

## 6. Curriculum, reviewer và validation

- [x] Admin xem/tạo/bật/tắt topic, module, level, lesson.
- [x] Reviewer resolve ambiguity và quản lý schema lifecycle.
- [x] Có benchmark, evaluation và validation backend.
- [x] Có màn hình admin curriculum và validation runs.
- [~] Reviewer frontend đã có nhiều tab nhưng cần kiểm thử end-to-end từng quyền.
- [ ] Chưa có quy trình phát hành curriculum version có phê duyệt và rollback đầy đủ.

## 7. Giao diện và route

- [x] Admin đã chuyển sang route riêng `/admin/*` với sidebar/topbar theo kiến trúc Learning Hub.
- [x] Action menu dùng overlay, SVG icon và modal form cho tạo/sửa tài khoản.
- [x] Đã sửa các chuỗi UTF-8 bị lỗi trong các màn hình B2B chính.
- [x] Có route `/school`, `/school/billing`, `/signup`, `/signup/payment-result`.
- [x] School Manager có portal lớp, báo cáo, import CSV, export và token audit.
- [x] Route admin `Feedback`, `Messages` đã dùng backend và modal thao tác, không còn placeholder.
- [!] Chưa có browser session khả dụng trong workspace để kiểm tra pixel/UI thực tế.
- [ ] Chưa có responsive QA đầy đủ trên mobile/tablet.

## 8. Dữ liệu, Supabase và vận hành

- [x] Có hướng dẫn kết nối Supabase pooler/direct và biến môi trường VNPAY.
- [x] Có seed role, plan và các ràng buộc role-school trong `data.sql`.
- [x] Có unit/security regression tests cho nhiều rule B2B.
- [x] Frontend production build đang chạy được.
- [!] Chưa chạy migration trên Supabase thật từ workspace này.
- [!] Chưa test với OpenRouter/VNPAY credential thật.
- [ ] Chưa có backup/restore, observability, audit log vận hành và alerting production.
- [ ] Chưa có CI pipeline bắt buộc build/test/migration check.

## 9. Thứ tự công việc tiếp theo

1. **Mở rộng grading:** bổ sung rubric/công thức theo từng solver và UI lịch sử nhiều lần làm bài.
2. **Hoàn thiện vận hành payment:** cấu hình IPN public, refund, auto-renew, email và test Sandbox thật.
3. **Chạy migration/QA:** migrate Supabase staging, test authorization matrix cho 5 role, responsive browser QA và kiểm thử tải.
4. **Bổ sung vận hành production:** backup/restore, observability, alerting và CI bắt buộc.

## 10. Điều kiện có thể gọi là hoàn thành MVP B2B

- [!] Một trường mới có thể đăng ký và thanh toán qua code Sandbox; cần merchant credentials/IPN public để có bằng chứng end-to-end.
- [x] Quản lý trường tạo được giáo viên/học sinh, lớp, phân giáo viên và enrollment; quota học sinh được kiểm tra.
- [x] Giáo viên chỉ giao bài trong lớp được phân công.
- [~] Học sinh làm bài, action log được lưu, auto-grade đáp án số và giáo viên xác nhận điểm; solver formula/realtime còn thiếu.
- [x] Shared Library có moderation bởi `REVIEWER` và audit quyết định.
- [x] License hết hạn chuyển đúng sang read-only cho các write flow B2B.
- [x] Admin xem được payment, plan, school, user, report và audit cần thiết.
- [ ] Supabase staging migration và VNPAY Sandbox test end-to-end đã có bằng chứng.
- [ ] Frontend production build, backend test và authorization matrix đều đạt trong CI.
## 11. Audit mã nguồn

- [x] Đã xác nhận và xóa các file frontend không còn import: `src/api/index.ts`, `src/types/index.ts`, `src/components/common/ProfileModal.tsx`, `src/components/simulation-canvas/renderers.ts`.
- [x] Đã xóa `AdminPlaceholderView` sau khi các route feedback/messages chuyển sang màn hình có backend thực.
- [x] Đã sửa các `useEffect` tải dữ liệu để không gọi state synchronously trong effect; frontend lint hiện không còn error/warning.
- [~] `AdminRoleViews.tsx` đang chứa nhiều màn hình admin; nên tách theo `UsersView`, `SchoolsView`, `PlansView`, `CurriculumView`, `ValidationView` khi có sprint refactor riêng.
- [~] `SchoolClasses.tsx` nên tách form, class table và members panel; hiện vẫn giữ chung để tránh thay đổi UI trong lúc audit.
- [~] `Workspace.tsx`, `StudentAssignments.tsx`, `CanvasRenderer.ts` là các file lớn có nhiều state/rendering; có thể tách hook và renderer subcomponent sau khi có test UI.
- [!] Action `Delete User` hiện đang gọi API suspend/soft-delete. Quy tắc B2B cấm hard delete, vì vậy cần chốt lại tên hiển thị (`Deactivate User`) hoặc giữ nhãn hiện tại như alias UX cho soft delete.
- [x] Không xóa các repository JPA chỉ đang được Spring scan hoặc entity relationship sử dụng; các repository chưa inject được ghi nhận là candidate, không xóa mù.
- [x] Hợp nhất CSS action overlay về một kích thước duy nhất, không còn style ghi đè làm menu hẹp/chữ nhỏ hoặc gạch chân.
- [x] Thêm thanh điều hướng admin ngang ở màn hình nhỏ; sidebar desktop không còn làm mất đường dẫn thao tác trên mobile.
- [x] Đồng bộ route workspace của giáo viên: legacy `/player`/`/models` giữ query, simulation và library item được phản ánh vào URL, route chờ auth trước khi render.
- [x] Admin dùng chung workspace/library/curriculum với giáo viên; endpoint danh sách học sinh chỉ trả học sinh trong lớp giáo viên được phân công.
