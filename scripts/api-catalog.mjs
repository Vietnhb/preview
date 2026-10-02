// Run backend ApiCatalogTest first; export compiled mappings + tested SecurityConfig role gates.
import { readFileSync, writeFileSync } from "node:fs";
const root = new URL("../", import.meta.url);
const catalog = JSON.parse(readFileSync(new URL("backend/target/api-catalog.json", root), "utf8"));
const purpose = {
  AdminController: { users: "Xem danh sách người dùng", createUser: "Tạo tài khoản; ADMIN chỉ được tạo MANAGER", updateUser: "Cập nhật tài khoản; ADMIN chỉ quản lý MANAGER", resetPassword: "Đặt lại mật khẩu tài khoản được phép quản lý", suspend: "Khóa tài khoản", restore: "Mở khóa tài khoản", toggleTopic: "Bật hoặc tắt chủ đề", validationMetrics: "Xem thống kê xác minh vật lý" },
  AdminOperationsController: { plans: "Xem các gói bản quyền", createPlan: "Tạo gói bản quyền", updatePlan: "Cập nhật gói bản quyền", toggleTopic: "Bật hoặc tắt chủ đề", validationMetrics: "Xem thống kê xác minh vật lý", schools: "Xem danh sách trường", createSchool: "Tạo trường", updateSchool: "Cập nhật trường và bản quyền", curriculum: "Xem toàn bộ cây chương trình học, kể cả mục đã tắt", validationRuns: "Xem các lần chạy xác minh" },
  AIController: { understandImage: "Hiểu mô tả JSON hoặc ảnh multipart, chọn schema và lập kế hoạch", understandText: "Hiểu mô tả JSON hoặc ảnh multipart, chọn schema và lập kế hoạch", generate: "Tạo phần trình bày mô phỏng từ kế hoạch đã ký", compute: "Tính lại dữ liệu vật lý từ kế hoạch và tham số", save: "Xác minh lại và lưu mô phỏng vào thư viện", openSaved: "Mở mô phỏng đã lưu theo quyền sở hữu", updateSavedVisual: "Cập nhật phần hiển thị của mô phỏng đã lưu" },
  AssignmentController: { create: "Tạo bài tập và cấp học sinh được làm", teacherAssignments: "Xem bài tập do mình giao", teacherClasses: "Xem các lớp giáo viên được phân công", studentAssignments: "Xem bài tập được giao cho mình", submit: "Lưu dự đoán của học sinh", complete: "Nộp hoàn tất bài tập", simulation: "Mở mô phỏng bài tập được cấp quyền", adjustSimulation: "Điều chỉnh tham số mô phỏng trong bài tập", submissions: "Xem bài nộp của bài tập mình quản lý", report: "Xem báo cáo kết quả bài tập", grade: "Chấm và xác nhận điểm bài nộp", reopen: "Mở lại bài nộp để học sinh làm tiếp" },
  AuthController: { login: "Đăng nhập", logout: "Đăng xuất", plans: "Xem các gói bản quyền đang bán" },
  BenchmarkReviewController: { list: "Xem bộ đề đánh giá", page: "Lọc và phân trang bộ đề đánh giá", create: "Tạo đề đánh giá nháp", updateDraft: "Sửa đề đánh giá nháp", activate: "Kích hoạt đề đánh giá", archive: "Lưu trữ đề đánh giá kèm lý do", annotate: "Gán nhãn chuẩn cho đề đánh giá", adjudicate: "Phân xử kết quả gán nhãn" },
  CurriculumAdminController: { topic: "Tạo chủ đề chương trình học", module: "Tạo module trong chủ đề", level: "Tạo cấp lớp trong module", lesson: "Tạo bài học trong cấp lớp", toggle: "Bật hoặc tắt nút chương trình học" },
  CurriculumController: { getTree: "Đọc cây chương trình học; khách chỉ thấy mục đang hoạt động" },
  EvaluationController: { history: "Xem lịch sử đánh giá", detail: "Xem chi tiết một lần đánh giá", compare: "So sánh hai lần đánh giá" },
  ExportController: { csv: "Xuất dữ liệu mô phỏng CSV", html: "Xuất mô phỏng HTML", json: "Xuất đặc tả và dữ liệu JSON", pdf: "Xuất báo cáo PDF", slides: "Xuất nội dung trình chiếu" },
  LibraryController: { search: "Tìm tài nguyên thuộc phạm vi được phép đọc", mine: "Xem thư viện cá nhân", community: "Xem tài nguyên cộng đồng đã duyệt theo phạm vi", save: "Lưu mô phỏng vào thư viện", rename: "Đổi tên tài nguyên của mình", move: "Chuyển tài nguyên của mình sang thư mục", clone: "Sao chép tài nguyên được phép đọc vào thư viện cá nhân", remove: "Gỡ tài nguyên của mình", discussion: "Đọc lượt thích, tổng bình luận và trang bình luận", comment: "Đăng bình luận vào tài nguyên đã duyệt", removeComment: "Xóa bình luận của mình hoặc theo quyền kiểm duyệt", react: "Đặt trạng thái thích hoặc bỏ thích; lặp yêu cầu không tăng trùng lượt" },
  LibraryFolderController: { mine: "Xem thư mục thư viện cá nhân", create: "Tạo thư mục", rename: "Đổi tên thư mục", remove: "Gỡ thư mục" },
  LibraryModerationController: { queue: "Xem hàng đợi kiểm duyệt public toàn hệ thống", page: "Lọc và phân trang hàng đợi public", moderate: "Duyệt, từ chối, gỡ hoặc đánh dấu nổi bật tài nguyên public", history: "Xem lịch sử kiểm duyệt tài nguyên" },
  ProblemController: { history: "Đọc lịch sử đề bài đã lưu", get: "Đọc đề bài thuộc quyền sở hữu", downloadAsset: "Đọc ảnh nguồn của đề bài thuộc quyền sở hữu" },
  RealtimeController: { events: "Nhận sự kiện cập nhật qua SSE" },
  ReviewerController: { openAmbiguities: "Xem các trường hợp đặc tả cần làm rõ", openAmbiguitiesPage: "Lọc và phân trang trường hợp cần làm rõ", claim: "Nhận xử lý một trường hợp cần làm rõ", release: "Trả trường hợp đã nhận về hàng đợi", resolve: "Giải quyết trường hợp cần làm rõ" },
  ReviewerModuleController: { list: "Xem các bản phát hành module", lifecycle: "Thay đổi trạng thái bản phát hành module" },
  ReviewerVersionsController: { schemas: "Xem các phiên bản schema để biên tập", updateSchema: "Sửa phiên bản schema nháp", schemaLifecycle: "Đổi trạng thái một phiên bản schema theo ID", implementations: "Xem các bộ giải số và bộ tham chiếu được hỗ trợ", solvers: "Xem phiên bản bộ giải", create: "Tạo phiên bản bộ giải nháp", update: "Sửa phiên bản bộ giải nháp", lifecycle: "Đổi trạng thái phiên bản bộ giải" },
  SchemaController: { list: "Xem schema; tài khoản thường chỉ thấy bản đã duyệt", get: "Đọc bản schema đã duyệt mới nhất", create: "Tạo phiên bản schema nháp", lifecycle: "Đổi trạng thái phiên bản mới nhất của schema", topicPackMetaSchema: "Đọc hợp đồng cấu trúc topic pack", coreTypeLibrary: "Đọc thư viện kiểu dữ liệu vật lý chuẩn" },
  SchoolAssignmentController: { list: "Xem giáo viên nào giao bài tập nào trong trường; STAFF cần là trưởng bộ môn" },
  SchoolClassController: { list: "Xem danh sách lớp được phép quản lý", get: "Xem giáo viên và học sinh trong lớp", create: "Tạo lớp; chỉ role SCHOOL được thực hiện", update: "Cập nhật lớp", archive: "Đóng lớp và kết thúc phân công/enrollment", assignTeacher: "Phân công giáo viên cho lớp", unassignTeacher: "Gỡ phân công giáo viên", enroll: "Ghi danh học sinh", transfer: "Chuyển học sinh sang lớp khác", remove: "Gỡ học sinh khỏi lớp" },
  SchoolImportController: { template: "Tải CSV mẫu có ví dụ cho loại import", previewFile: "Đọc CSV hoặc dữ liệu JSON và kiểm tra trước import", previewRows: "Đọc CSV hoặc dữ liệu JSON và kiểm tra trước import", commit: "Nhập dữ liệu đã kiểm tra; loại import và quyền được xác minh ở service" },
  SchoolLibraryModerationController: { queue: "Xem hàng đợi mô phỏng chia sẻ nội bộ trường", moderate: "Duyệt hoặc gỡ mô phỏng nội bộ; STAFF cần là trưởng bộ môn" },
  SchoolManagementController: { list: "Xem tài khoản trong trường", create: "Tạo tài khoản cấp trường, yêu cầu đổi mật khẩu lần đầu", update: "Cập nhật tài khoản cấp trường", resetPassword: "Đặt lại mật khẩu tài khoản trong trường", suspend: "Khóa tài khoản trong trường", restore: "Mở khóa tài khoản trong trường" },
  SchoolPaymentController: { checkout: "Tạo thanh toán đăng ký trường", recover: "Khôi phục thanh toán bằng thông tin đăng ký hợp lệ", ipn: "Nhận IPN có xác minh chữ ký từ cổng thanh toán", status: "Đọc trạng thái thanh toán theo mã", billing: "Xem bản quyền và thông tin thanh toán của trường", quote: "Báo giá nâng gói hoặc gia hạn, chặn hạ gói đang hoạt động", purchase: "Tạo thanh toán nâng gói hoặc gia hạn", notifications: "Xem thông báo thanh toán cho quản lý hệ thống", adminPayments: "Xem giao dịch toàn hệ thống", paymentReport: "Xem báo cáo doanh thu", reconcile: "Đối soát giao dịch với cổng thanh toán" },
  SchoolReportController: { summary: "Xem tổng quan hoạt động trường", classes: "Xem báo cáo lớp trong trường", tokenAudit: "Xem lịch sử sử dụng token AI của trường", exportClasses: "Xuất báo cáo lớp CSV" },
  SimulationController: { history: "Xem lịch sử mô phỏng của mình", recent: "Xem tóm tắt mô phỏng gần đây", get: "Mở mô phỏng thuộc quyền sở hữu", getShared: "Mở mô phỏng được chia sẻ và đã duyệt; moderator có thể xem theo quyền kiểm duyệt" },
  SpecificationController: { get: "Đọc đặc tả mô phỏng được phép truy cập", confirmAmbiguity: "Xác nhận dữ kiện cần làm rõ của đặc tả", readiness: "Đọc điều kiện sẵn sàng xác minh vật lý" },
  StudentActionLogController: { create: "Ghi nhật ký tương tác khi làm bài", mine: "Xem nhật ký tương tác của mình" },
  StudentClassController: { mine: "Xem lớp học, giáo viên và số bạn cùng lớp của mình" },
  SupportController: { feedback: "Gửi phản hồi", message: "Gửi thông báo sự cố", mine: "Xem phản hồi và sự cố mình đã gửi", adminList: "Quản lý danh sách phản hồi/sự cố toàn hệ thống", update: "Cập nhật trạng thái xử lý phản hồi/sự cố" },
  UserController: { getMe: "Đọc hồ sơ tài khoản hiện tại", updateProfile: "Cập nhật tên và ngày sinh", updateAvatar: "Cập nhật đường dẫn avatar", changePassword: "Đổi mật khẩu và hoàn tất yêu cầu đổi lần đầu", license: "Đọc trạng thái bản quyền của tài khoản", getStudents: "Xem học sinh mà giáo viên được phép giao bài" },
};
const roles = {
  GUEST: "Xem cộng đồng PUBLIC đã duyệt và bình luận; đăng nhập, đăng ký trường, xem gói và luồng thanh toán public. Không viết bình luận hoặc thích.",
  ADMIN: "Xem toàn bộ người dùng và tạo/quản lý MANAGER. Service chặn quản lý các role khác.",
  MANAGER: "Quản lý người dùng toàn hệ thống, trường/gói/chương trình học, phản hồi và sự cố; quản lý nội dung và đánh giá.",
  REVIEWER: "Biên tập ngữ cảnh/schema/bộ giải khi có quyền CONTENT_EDIT; kiểm duyệt public và xóa bình luận public khi có quyền CONTENT_REVIEW. Hai quyền độc lập, do MANAGER gắn.",
  SCHOOL: "Mua/nâng/gia hạn bản quyền, tạo lớp, quản lý tài khoản và mật khẩu trong trường, import CSV và báo cáo.",
  STAFF: "Quyền TEACH: tạo mô phỏng, quản lý thư viện và bài tập của lớp được phân công. Quyền DEPARTMENT_HEAD_PHYSICS: phân công giáo viên/học sinh và duyệt nội bộ. Một tài khoản có thể có một hoặc cả hai quyền, do SCHOOL gắn. STAFF không được tạo lớp.",
  STUDENT: "Xem lớp, làm bài được cấp quyền, nộp bài, nhận điểm, tương tác cộng đồng thuộc phạm vi được đọc.",
};
const lines = ["# Danh mục API PhysLive", "", `Tổng: **${catalog.total} API**. Một API là một cặp HTTP method + path; JSON/multipart cùng đường dẫn tính một API. Không tính Swagger, Actuator và WebSocket handshake.`, "",
  "Nguồn: mapping của controller đã biên dịch và kiểm tra SecurityConfig bằng MockMvc trong ApiCatalogTest. Đây là quyền qua lớp HTTP; service tiếp tục kiểm tra quyền sở hữu, trường, bản quyền, mật khẩu lần đầu và capability. Vì vậy số API HTTP không đồng nghĩa mỗi tài khoản được phép dùng mọi tài nguyên.", "",
  "## Nhiệm vụ theo role", ""];
for (const [role, description] of Object.entries(roles)) {
  const count = catalog.endpoints.filter(endpoint => endpoint.roles.includes(role)).length;
  const publicCount = catalog.endpoints.filter(endpoint => endpoint.roles.includes(role) && endpoint.roles.includes("GUEST")).length;
  lines.push(`- **${role}: ${count} API qua HTTP**, gồm ${publicCount} API public. ${description}`);
}
lines.push("", "ADMIN và các role đăng nhập vẫn có API hồ sơ cá nhân. Các số trên có giao nhau vì nhiều role dùng chung một API; không cộng các số theo role để tính tổng.", "", "## Danh sách đầy đủ", "");
for (const controller of [...new Set(catalog.endpoints.map(endpoint => endpoint.controller))].sort()) {
  const endpoints = catalog.endpoints.filter(endpoint => endpoint.controller === controller);
  if (!endpoints.length) continue;
  lines.push(`### ${controller} (${endpoints.length})`, "");
  for (const endpoint of endpoints) {
    const task = purpose[controller]?.[endpoint.handler];
    if (!task) throw new Error(`Missing purpose: ${controller}.${endpoint.handler}`);
    lines.push(`- \`${endpoint.method} ${endpoint.path}\` — ${task}. Quyền HTTP: ${endpoint.roles.map(role => `\`${role}\``).join(", ")}.`);
  }
  lines.push("");
}
lines.push("## Tạo lại danh mục", "", "Từ thư mục backend: `./mvnw -Dtest=ApiCatalogTest test`. Sau đó từ thư mục repository: `node scripts/api-catalog.mjs`.", "");
writeFileSync(new URL("docs/implementation/api-catalog.md", root), lines.join("\n"));
console.log(`Exported ${catalog.total} API with roles and purposes.`);
