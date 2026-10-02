# Danh mục API PhysLive

Tổng: **162 API**. Một API là một cặp HTTP method + path; JSON/multipart cùng đường dẫn tính một API. Không tính Swagger, Actuator và WebSocket handshake.

Nguồn: mapping của controller đã biên dịch và kiểm tra SecurityConfig bằng MockMvc trong ApiCatalogTest. Đây là quyền qua lớp HTTP; service tiếp tục kiểm tra quyền sở hữu, trường, bản quyền, mật khẩu lần đầu và capability. Vì vậy số API HTTP không đồng nghĩa mỗi tài khoản được phép dùng mọi tài nguyên.

## Nhiệm vụ theo role

- **GUEST: 11 API qua HTTP**, gồm 11 API public. Xem cộng đồng PUBLIC đã duyệt và bình luận; đăng nhập, đăng ký trường, xem gói và luồng thanh toán public. Không viết bình luận hoặc thích.
- **ADMIN: 22 API qua HTTP**, gồm 11 API public. Xem toàn bộ người dùng và tạo/quản lý MANAGER. Service chặn quản lý các role khác.
- **MANAGER: 151 API qua HTTP**, gồm 11 API public. Quản lý người dùng toàn hệ thống, trường/gói/chương trình học, phản hồi và sự cố; quản lý nội dung và đánh giá.
- **REVIEWER: 71 API qua HTTP**, gồm 11 API public. Biên tập ngữ cảnh/schema/bộ giải khi có quyền CONTENT_EDIT; kiểm duyệt public và xóa bình luận public khi có quyền CONTENT_REVIEW. Hai quyền độc lập, do MANAGER gắn.
- **SCHOOL: 68 API qua HTTP**, gồm 11 API public. Mua/nâng/gia hạn bản quyền, tạo lớp, quản lý tài khoản và mật khẩu trong trường, import CSV và báo cáo.
- **STAFF: 83 API qua HTTP**, gồm 11 API public. Quyền TEACH: tạo mô phỏng, quản lý thư viện và bài tập của lớp được phân công. Quyền DEPARTMENT_HEAD_PHYSICS: phân công giáo viên/học sinh và duyệt nội bộ. Một tài khoản có thể có một hoặc cả hai quyền, do SCHOOL gắn. STAFF không được tạo lớp.
- **STUDENT: 47 API qua HTTP**, gồm 11 API public. Xem lớp, làm bài được cấp quyền, nộp bài, nhận điểm, tương tác cộng đồng thuộc phạm vi được đọc.

ADMIN và các role đăng nhập vẫn có API hồ sơ cá nhân. Các số trên có giao nhau vì nhiều role dùng chung một API; không cộng các số theo role để tính tổng.

## Danh sách đầy đủ

### AIController (6)

- `GET /api/simulation/saved/{id}` — Mở mô phỏng đã lưu theo quyền sở hữu. Quyền HTTP: `MANAGER`, `STAFF`.
- `PATCH /api/simulation/saved/{id}/visual` — Cập nhật phần hiển thị của mô phỏng đã lưu. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/simulation/compute` — Tính lại dữ liệu vật lý từ kế hoạch và tham số. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/simulation/generate` — Tạo phần trình bày mô phỏng từ kế hoạch đã ký. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/simulation/saved` — Xác minh lại và lưu mô phỏng vào thư viện. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/simulation/understand` — Hiểu mô tả JSON hoặc ảnh multipart, chọn schema và lập kế hoạch. Quyền HTTP: `MANAGER`, `STAFF`.

### AdminController (6)

- `GET /api/admin/users` — Xem danh sách người dùng. Quyền HTTP: `ADMIN`, `MANAGER`.
- `POST /api/admin/users` — Tạo tài khoản; ADMIN chỉ được tạo MANAGER. Quyền HTTP: `ADMIN`, `MANAGER`.
- `POST /api/admin/users/{id}/reset-password` — Đặt lại mật khẩu tài khoản được phép quản lý. Quyền HTTP: `ADMIN`, `MANAGER`.
- `PUT /api/admin/users/{id}` — Cập nhật tài khoản; ADMIN chỉ quản lý MANAGER. Quyền HTTP: `ADMIN`, `MANAGER`.
- `PUT /api/admin/users/{id}/restore` — Mở khóa tài khoản. Quyền HTTP: `ADMIN`, `MANAGER`.
- `PUT /api/admin/users/{id}/suspend` — Khóa tài khoản. Quyền HTTP: `ADMIN`, `MANAGER`.

### AdminOperationsController (10)

- `GET /api/admin/curriculum` — Xem toàn bộ cây chương trình học, kể cả mục đã tắt. Quyền HTTP: `MANAGER`.
- `GET /api/admin/metrics/validation` — Xem thống kê xác minh vật lý. Quyền HTTP: `MANAGER`.
- `GET /api/admin/plans` — Xem các gói bản quyền. Quyền HTTP: `MANAGER`.
- `GET /api/admin/schools` — Xem danh sách trường. Quyền HTTP: `MANAGER`.
- `GET /api/admin/validation-runs` — Xem các lần chạy xác minh. Quyền HTTP: `MANAGER`.
- `POST /api/admin/plans` — Tạo gói bản quyền. Quyền HTTP: `MANAGER`.
- `POST /api/admin/schools` — Tạo trường. Quyền HTTP: `MANAGER`.
- `PUT /api/admin/plans/{code}` — Cập nhật gói bản quyền. Quyền HTTP: `MANAGER`.
- `PUT /api/admin/schools/{id}` — Cập nhật trường và bản quyền. Quyền HTTP: `MANAGER`.
- `PUT /api/admin/topics/{id}/toggle` — Bật hoặc tắt chủ đề. Quyền HTTP: `MANAGER`.

### AssignmentController (12)

- `GET /api/assignments/mine/student` — Xem bài tập được giao cho mình. Quyền HTTP: `STUDENT`.
- `GET /api/assignments/mine/teacher` — Xem bài tập do mình giao. Quyền HTTP: `MANAGER`, `STAFF`.
- `GET /api/assignments/mine/teacher/classes` — Xem các lớp giáo viên được phân công. Quyền HTTP: `MANAGER`, `STAFF`.
- `GET /api/assignments/{id}/report` — Xem báo cáo kết quả bài tập. Quyền HTTP: `MANAGER`, `STAFF`.
- `GET /api/assignments/{id}/simulation` — Mở mô phỏng bài tập được cấp quyền. Quyền HTTP: `STUDENT`.
- `GET /api/assignments/{id}/submissions` — Xem bài nộp của bài tập mình quản lý. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/assignments` — Tạo bài tập và cấp học sinh được làm. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/assignments/{assignmentId}/submissions/{submissionId}/reopen` — Mở lại bài nộp để học sinh làm tiếp. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/assignments/{id}/predictions` — Lưu dự đoán của học sinh. Quyền HTTP: `STUDENT`.
- `POST /api/assignments/{id}/simulation/adjust` — Điều chỉnh tham số mô phỏng trong bài tập. Quyền HTTP: `STUDENT`.
- `POST /api/assignments/{id}/submit` — Nộp hoàn tất bài tập. Quyền HTTP: `STUDENT`.
- `PUT /api/assignments/{assignmentId}/submissions/{submissionId}/grade` — Chấm và xác nhận điểm bài nộp. Quyền HTTP: `MANAGER`, `STAFF`.

### AuthController (3)

- `GET /api/auth/plans` — Xem các gói bản quyền đang bán. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `POST /api/auth/login` — Đăng nhập. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `POST /api/auth/logout` — Đăng xuất. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.

### BenchmarkReviewController (8)

- `GET /api/reviewer/benchmarks` — Xem bộ đề đánh giá. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `GET /api/reviewer/benchmarks/page` — Lọc và phân trang bộ đề đánh giá. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `POST /api/reviewer/benchmarks` — Tạo đề đánh giá nháp. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `POST /api/reviewer/benchmarks/{id}/activate` — Kích hoạt đề đánh giá. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `POST /api/reviewer/benchmarks/{id}/adjudication` — Phân xử kết quả gán nhãn. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `POST /api/reviewer/benchmarks/{id}/annotations` — Gán nhãn chuẩn cho đề đánh giá. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `POST /api/reviewer/benchmarks/{id}/archive` — Lưu trữ đề đánh giá kèm lý do. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `PUT /api/reviewer/benchmarks/{id}` — Sửa đề đánh giá nháp. Quyền HTTP: `MANAGER`, `REVIEWER`.

### CurriculumAdminController (5)

- `POST /api/admin/curriculum/levels/{levelId}/lessons` — Tạo bài học trong cấp lớp. Quyền HTTP: `MANAGER`.
- `POST /api/admin/curriculum/modules/{moduleId}/levels` — Tạo cấp lớp trong module. Quyền HTTP: `MANAGER`.
- `POST /api/admin/curriculum/topics` — Tạo chủ đề chương trình học. Quyền HTTP: `MANAGER`.
- `POST /api/admin/curriculum/topics/{topicId}/modules` — Tạo module trong chủ đề. Quyền HTTP: `MANAGER`.
- `PUT /api/admin/curriculum/{type}/{id}/toggle` — Bật hoặc tắt nút chương trình học. Quyền HTTP: `MANAGER`.

### CurriculumController (1)

- `GET /api/curriculum` — Đọc cây chương trình học; khách chỉ thấy mục đang hoạt động. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.

### EvaluationController (3)

- `GET /api/evaluations/compare` — So sánh hai lần đánh giá. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `GET /api/evaluations/history` — Xem lịch sử đánh giá. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `GET /api/evaluations/{id}` — Xem chi tiết một lần đánh giá. Quyền HTTP: `MANAGER`, `REVIEWER`.

### ExportController (5)

- `GET /api/exports/{specificationId}/csv` — Xuất dữ liệu mô phỏng CSV. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/exports/{specificationId}/html` — Xuất mô phỏng HTML. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/exports/{specificationId}/json` — Xuất đặc tả và dữ liệu JSON. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/exports/{specificationId}/pdf` — Xuất báo cáo PDF. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/exports/{specificationId}/slides` — Xuất nội dung trình chiếu. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.

### LibraryController (12)

- `DELETE /api/library/{id}` — Gỡ tài nguyên của mình. Quyền HTTP: `MANAGER`, `STAFF`.
- `DELETE /api/library/{id}/comments/{commentId}` — Xóa bình luận của mình hoặc theo quyền kiểm duyệt. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/library` — Tìm tài nguyên thuộc phạm vi được phép đọc. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/library/community` — Xem tài nguyên cộng đồng đã duyệt theo phạm vi. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/library/mine` — Xem thư viện cá nhân. Quyền HTTP: `MANAGER`, `STAFF`.
- `GET /api/library/{id}/discussion` — Đọc lượt thích, tổng bình luận và trang bình luận. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `PATCH /api/library/{id}` — Đổi tên tài nguyên của mình. Quyền HTTP: `MANAGER`, `STAFF`.
- `PATCH /api/library/{id}/folder` — Chuyển tài nguyên của mình sang thư mục. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/library` — Lưu mô phỏng vào thư viện. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/library/{id}/clone` — Sao chép tài nguyên được phép đọc vào thư viện cá nhân. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/library/{id}/comments` — Đăng bình luận vào tài nguyên đã duyệt. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `PUT /api/library/{id}/reaction` — Đặt trạng thái thích hoặc bỏ thích; lặp yêu cầu không tăng trùng lượt. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.

### LibraryFolderController (4)

- `DELETE /api/library/folders/{id}` — Gỡ thư mục. Quyền HTTP: `MANAGER`, `STAFF`.
- `GET /api/library/folders` — Xem thư mục thư viện cá nhân. Quyền HTTP: `MANAGER`, `STAFF`.
- `PATCH /api/library/folders/{id}` — Đổi tên thư mục. Quyền HTTP: `MANAGER`, `STAFF`.
- `POST /api/library/folders` — Tạo thư mục. Quyền HTTP: `MANAGER`, `STAFF`.

### LibraryModerationController (4)

- `GET /api/reviewer/library` — Xem hàng đợi kiểm duyệt public toàn hệ thống. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `GET /api/reviewer/library/page` — Lọc và phân trang hàng đợi public. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `GET /api/reviewer/library/{id}/history` — Xem lịch sử kiểm duyệt tài nguyên. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `PUT /api/reviewer/library/{id}` — Duyệt, từ chối, gỡ hoặc đánh dấu nổi bật tài nguyên public. Quyền HTTP: `MANAGER`, `REVIEWER`.

### ProblemController (3)

- `GET /api/problems` — Đọc lịch sử đề bài đã lưu. Quyền HTTP: `MANAGER`, `STAFF`.
- `GET /api/problems/assets/{assetId}/content` — Đọc ảnh nguồn của đề bài thuộc quyền sở hữu. Quyền HTTP: `MANAGER`, `STAFF`.
- `GET /api/problems/{id}` — Đọc đề bài thuộc quyền sở hữu. Quyền HTTP: `MANAGER`, `STAFF`.

### RealtimeController (1)

- `GET /api/realtime/events` — Nhận sự kiện cập nhật qua SSE. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.

### ReviewerController (5)

- `GET /api/reviewer/ambiguities` — Xem các trường hợp đặc tả cần làm rõ. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `GET /api/reviewer/ambiguities/page` — Lọc và phân trang trường hợp cần làm rõ. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `POST /api/reviewer/ambiguities/{ambiguityId}/claim` — Nhận xử lý một trường hợp cần làm rõ. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `POST /api/reviewer/ambiguities/{ambiguityId}/release` — Trả trường hợp đã nhận về hàng đợi. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `POST /api/reviewer/ambiguities/{ambiguityId}/resolve` — Giải quyết trường hợp cần làm rõ. Quyền HTTP: `MANAGER`, `REVIEWER`.

### ReviewerModuleController (2)

- `GET /api/reviewer/module-releases` — Xem các bản phát hành module. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `PUT /api/reviewer/module-releases/{id}/lifecycle` — Thay đổi trạng thái bản phát hành module. Quyền HTTP: `MANAGER`, `REVIEWER`.

### ReviewerVersionsController (8)

- `GET /api/reviewer/schemas` — Xem các phiên bản schema để biên tập. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `GET /api/reviewer/solver-implementations` — Xem các bộ giải số và bộ tham chiếu được hỗ trợ. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `GET /api/reviewer/solvers` — Xem phiên bản bộ giải. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `POST /api/reviewer/solvers` — Tạo phiên bản bộ giải nháp. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `PUT /api/reviewer/schema-versions/{id}` — Sửa phiên bản schema nháp. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `PUT /api/reviewer/schema-versions/{id}/lifecycle` — Đổi trạng thái một phiên bản schema theo ID. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `PUT /api/reviewer/solvers/{id}` — Sửa phiên bản bộ giải nháp. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `PUT /api/reviewer/solvers/{id}/lifecycle` — Đổi trạng thái phiên bản bộ giải. Quyền HTTP: `MANAGER`, `REVIEWER`.

### SchemaController (6)

- `GET /api/schemas` — Xem schema; tài khoản thường chỉ thấy bản đã duyệt. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/schemas/core-types` — Đọc thư viện kiểu dữ liệu vật lý chuẩn. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/schemas/meta-schema` — Đọc hợp đồng cấu trúc topic pack. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/schemas/{schemaId}` — Đọc bản schema đã duyệt mới nhất. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `POST /api/schemas` — Tạo phiên bản schema nháp. Quyền HTTP: `MANAGER`, `REVIEWER`.
- `PUT /api/schemas/{schemaId}/lifecycle` — Đổi trạng thái phiên bản mới nhất của schema. Quyền HTTP: `MANAGER`, `REVIEWER`.

### SchoolAssignmentController (1)

- `GET /api/schools/{schoolId}/assignments` — Xem giáo viên nào giao bài tập nào trong trường; STAFF cần là trưởng bộ môn. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.

### SchoolClassController (10)

- `DELETE /api/schools/{schoolId}/classes/{classId}` — Đóng lớp và kết thúc phân công/enrollment. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `DELETE /api/schools/{schoolId}/classes/{classId}/students/{studentId}` — Gỡ học sinh khỏi lớp. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `DELETE /api/schools/{schoolId}/classes/{classId}/teachers/{teacherId}` — Gỡ phân công giáo viên. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `GET /api/schools/{schoolId}/classes` — Xem danh sách lớp được phép quản lý. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `GET /api/schools/{schoolId}/classes/{classId}` — Xem giáo viên và học sinh trong lớp. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `POST /api/schools/{schoolId}/classes` — Tạo lớp; chỉ role SCHOOL được thực hiện. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `POST /api/schools/{schoolId}/classes/{classId}/students` — Ghi danh học sinh. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `POST /api/schools/{schoolId}/classes/{classId}/teachers` — Phân công giáo viên cho lớp. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `PUT /api/schools/{schoolId}/classes/{classId}` — Cập nhật lớp. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `PUT /api/schools/{schoolId}/classes/{classId}/students/{studentId}/transfer` — Chuyển học sinh sang lớp khác. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.

### SchoolImportController (3)

- `GET /api/schools/{schoolId}/imports/template` — Tải CSV mẫu có ví dụ cho loại import. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `POST /api/schools/{schoolId}/imports/commit` — Nhập dữ liệu đã kiểm tra; loại import và quyền được xác minh ở service. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `POST /api/schools/{schoolId}/imports/preview` — Đọc CSV hoặc dữ liệu JSON và kiểm tra trước import. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.

### SchoolLibraryModerationController (2)

- `GET /api/schools/{schoolId}/library` — Xem hàng đợi mô phỏng chia sẻ nội bộ trường. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `PUT /api/schools/{schoolId}/library/{id}` — Duyệt hoặc gỡ mô phỏng nội bộ; STAFF cần là trưởng bộ môn. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.

### SchoolManagementController (6)

- `GET /api/schools/{schoolId}/users` — Xem tài khoản trong trường. Quyền HTTP: `MANAGER`, `SCHOOL`, `STAFF`.
- `POST /api/schools/{schoolId}/users` — Tạo tài khoản cấp trường, yêu cầu đổi mật khẩu lần đầu. Quyền HTTP: `MANAGER`, `SCHOOL`.
- `POST /api/schools/{schoolId}/users/{userId}/reset-password` — Đặt lại mật khẩu tài khoản trong trường. Quyền HTTP: `MANAGER`, `SCHOOL`.
- `PUT /api/schools/{schoolId}/users/{userId}` — Cập nhật tài khoản cấp trường. Quyền HTTP: `MANAGER`, `SCHOOL`.
- `PUT /api/schools/{schoolId}/users/{userId}/restore` — Mở khóa tài khoản trong trường. Quyền HTTP: `MANAGER`, `SCHOOL`.
- `PUT /api/schools/{schoolId}/users/{userId}/suspend` — Khóa tài khoản trong trường. Quyền HTTP: `MANAGER`, `SCHOOL`.

### SchoolPaymentController (11)

- `GET /api/admin/payment-notifications` — Xem thông báo thanh toán cho quản lý hệ thống. Quyền HTTP: `MANAGER`.
- `GET /api/admin/payment-report` — Xem báo cáo doanh thu. Quyền HTTP: `MANAGER`.
- `GET /api/admin/payments` — Xem giao dịch toàn hệ thống. Quyền HTTP: `MANAGER`.
- `GET /api/auth/payments/vnpay/ipn` — Nhận IPN có xác minh chữ ký từ cổng thanh toán. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/auth/payments/{id}` — Đọc trạng thái thanh toán theo mã. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/school/billing` — Xem bản quyền và thông tin thanh toán của trường. Quyền HTTP: `SCHOOL`.
- `POST /api/admin/payments/{id}/reconcile` — Đối soát giao dịch với cổng thanh toán. Quyền HTTP: `MANAGER`.
- `POST /api/auth/school-checkout` — Tạo thanh toán đăng ký trường. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `POST /api/auth/school-checkout/recover` — Khôi phục thanh toán bằng thông tin đăng ký hợp lệ. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `POST /api/school/billing/checkout` — Tạo thanh toán nâng gói hoặc gia hạn. Quyền HTTP: `SCHOOL`.
- `POST /api/school/billing/quote` — Báo giá nâng gói hoặc gia hạn, chặn hạ gói đang hoạt động. Quyền HTTP: `SCHOOL`.

### SchoolReportController (4)

- `GET /api/schools/{schoolId}/reports/classes` — Xem báo cáo lớp trong trường. Quyền HTTP: `MANAGER`, `SCHOOL`.
- `GET /api/schools/{schoolId}/reports/classes.csv` — Xuất báo cáo lớp CSV. Quyền HTTP: `MANAGER`, `SCHOOL`.
- `GET /api/schools/{schoolId}/reports/summary` — Xem tổng quan hoạt động trường. Quyền HTTP: `MANAGER`, `SCHOOL`.
- `GET /api/schools/{schoolId}/reports/token-audit` — Xem lịch sử sử dụng token AI của trường. Quyền HTTP: `MANAGER`, `SCHOOL`.

### SimulationController (4)

- `GET /api/simulations` — Xem lịch sử mô phỏng của mình. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/simulations/recent` — Xem tóm tắt mô phỏng gần đây. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/simulations/shared/{id}` — Mở mô phỏng được chia sẻ và đã duyệt; moderator có thể xem theo quyền kiểm duyệt. Quyền HTTP: `GUEST`, `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/simulations/{id}` — Mở mô phỏng thuộc quyền sở hữu. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.

### SpecificationController (3)

- `GET /api/specifications/{id}` — Đọc đặc tả mô phỏng được phép truy cập. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/specifications/{id}/validation-readiness` — Đọc điều kiện sẵn sàng xác minh vật lý. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `POST /api/specifications/{specificationId}/ambiguities/{ambiguityId}/confirm` — Xác nhận dữ kiện cần làm rõ của đặc tả. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.

### StudentActionLogController (2)

- `GET /api/student/action-logs` — Xem nhật ký tương tác của mình. Quyền HTTP: `STUDENT`.
- `POST /api/student/action-logs` — Ghi nhật ký tương tác khi làm bài. Quyền HTTP: `STUDENT`.

### StudentClassController (1)

- `GET /api/student/classes` — Xem lớp học, giáo viên và số bạn cùng lớp của mình. Quyền HTTP: `STUDENT`.

### SupportController (5)

- `GET /api/support/admin` — Quản lý danh sách phản hồi/sự cố toàn hệ thống. Quyền HTTP: `MANAGER`.
- `GET /api/support/mine` — Xem phản hồi và sự cố mình đã gửi. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `POST /api/support/feedback` — Gửi phản hồi. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `POST /api/support/messages` — Gửi thông báo sự cố. Quyền HTTP: `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `PUT /api/support/admin/{id}` — Cập nhật trạng thái xử lý phản hồi/sự cố. Quyền HTTP: `MANAGER`.

### UserController (6)

- `GET /api/user/me` — Đọc hồ sơ tài khoản hiện tại. Quyền HTTP: `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/user/me/license` — Đọc trạng thái bản quyền của tài khoản. Quyền HTTP: `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `GET /api/user/students` — Xem học sinh mà giáo viên được phép giao bài. Quyền HTTP: `MANAGER`, `STAFF`.
- `PUT /api/user/me/avatar` — Cập nhật đường dẫn avatar. Quyền HTTP: `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `PUT /api/user/me/password` — Đổi mật khẩu và hoàn tất yêu cầu đổi lần đầu. Quyền HTTP: `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.
- `PUT /api/user/me/profile` — Cập nhật tên và ngày sinh. Quyền HTTP: `ADMIN`, `MANAGER`, `REVIEWER`, `SCHOOL`, `STAFF`, `STUDENT`.

## Tạo lại danh mục

Từ thư mục backend: `./mvnw -Dtest=ApiCatalogTest test`. Sau đó từ thư mục repository: `node scripts/api-catalog.mjs`.
