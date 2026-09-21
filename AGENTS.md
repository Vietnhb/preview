# PhysLive — Hướng dẫn làm việc cho agent

Áp dụng cho toàn bộ project `physLive_preview`. Đọc hướng dẫn này trước khi sửa code; đọc thêm hướng dẫn trong thư mục con nếu có.

## Ưu tiên cao nhất: tránh hardcode nghiệp vụ và logic cứng

- Hành vi có thể thay đổi theo schema, đề bài, cấu hình hoặc phiên bản phải lấy từ contract/config đã được validate, không chôn trong controller, solver hoặc renderer.
- Không viết chuỗi `if/switch` theo schema ID, tên bài, câu nhập, ngôn ngữ hay ví dụ benchmark để ép ra kết quả mong muốn. Dùng capability, binding hoặc registry hiện có khi phù hợp.
- Không hardcode số vật thể, asset, tham số, đơn vị, giới hạn, duration, series hoặc kết quả cho mọi mô phỏng. Lấy từ specification và schema/output contract được hỗ trợ.
- Không tự mặc định ba vật thành một vật, bỏ quan hệ, hoặc đổi mô hình vật lý chỉ để chạy được. Trường hợp chưa hỗ trợ phải trả trạng thái/lý do rõ ràng hoặc yêu cầu làm rõ theo workflow.
- Không tạo dữ liệu, điểm số, trạng thái PASS/APPROVED hay kết quả solver giả để hoàn tất UI hoặc test.
- Mọi cấu hình mới phải có nguồn sở hữu rõ ràng, validation và quy tắc version khi ảnh hưởng kết quả; ưu tiên nguồn hiện có, tránh khai báo cùng dữ liệu ở nhiều nơi.
- Không biến mọi hằng số thành cấu hình. Công thức vật lý, bất biến bảo mật, enum hữu hạn, giao thức và thuật toán chuyên biệt có thể nằm trong code. Hằng số vật lý phải có đơn vị và giả thiết rõ ràng; không dùng tên “linh hoạt” để bỏ ràng buộc học thuật.
- Không xây rule engine, plugin framework hay hệ thống abstraction mới nếu schema/config/registry hiện tại đã giải quyết được nhu cầu.

## Đơn giản hóa để bảo trì

- Đọc luồng thực tế và test trước khi thay đổi. Phân biệt sửa bug với refactor giữ nguyên hành vi.
- Ưu tiên giải pháp nhỏ, dễ đọc, tái sử dụng phần đúng; không viết lại toàn bộ hoặc chia microservices khi chưa có nhu cầu được chứng minh.
- Controller nhận request, validation đầu vào, gọi service và trả response. Quyết định nghiệp vụ, lifecycle và transaction thuộc service; truy vấn thuộc repository.
- Chỉ tách lớp khi có trách nhiệm độc lập. Không tách mỗi method thành một file, không tạo interface/wrapper/base class chỉ để đủ pattern.
- Dùng tên rõ nghĩa, import thông thường, mỗi câu lệnh một dòng. Số dòng và số file không phải thước đo chất lượng duy nhất.
- Chuẩn hóa DTO/error/pagination theo convention hiện có. Tránh trả JPA entity có quan hệ hoặc dữ liệu nội bộ trực tiếp qua API.
- Không xóa compatibility adapter hoặc migration chỉ vì tên có “legacy”. Kiểm tra dữ liệu lịch sử, replay và consumer trước.

## Phân quyền và phạm vi dữ liệu

- Tập trung quyền HTTP endpoint trong `SecurityConfig`; không tạo thêm bộ annotation role riêng hoặc lặp policy ở controller.
- Đối chiếu method + URL thực tế; matcher cụ thể đứng trước matcher rộng. Không để endpoint nhạy cảm rơi về `authenticated()` ngoài chủ ý.
- Service vẫn kiểm tra ownership, school/tenant, quan hệ teacher–class–student và tính độc lập reviewer. Những kiểm tra này không được thay bằng role matcher.
- Giữ tương thích giá trị role trong database/JWT; alias ngắn trong code không đồng nghĩa được đổi role lưu trữ.
- Khi sửa authorization phải có test đi qua SecurityFilterChain thật: chưa đăng nhập, sai role, đúng role; request bị từ chối không được thực hiện nghiệp vụ.

## AI, schema và tính đúng học thuật

- AI diễn giải đầu vào và tạo specification trong capability đã hỗ trợ. Backend chịu trách nhiệm validate đơn vị, quantities, objects/relations, binding và kết quả.
- Không cho AI tự approve schema, solver, module hoặc benchmark gold; không tin output AI chỉ vì parse JSON thành công.
- Giữ rõ Numerical Solver và Closed-form/reference strategy. Không gọi lại cùng đường thực thi rồi tuyên bố hai cách giải độc lập.
- Nếu không có closed-form độc lập, biểu diễn capability và lý do học thuật trung thực; áp dụng workflow validation/review đã định nghĩa. Không tự gắn PASS hoặc thay đổi điều kiện cho phép simulation.
- Bảo toàn tolerance, checkpoints, điều kiện kết thúc, đơn vị, output contract và cách xác định sai số.
- Schema/solver đã approved phải bất biến về nội dung; thay đổi tạo version mới. Giữ identity, checksum deterministic, version pinning và historical replay.
- Asset/animation phải bám dữ liệu solver. Không dùng animation hoặc biểu đồ mẫu làm bằng chứng mô phỏng đúng vật lý.

## Transaction, external API và database

- Tránh giữ transaction DB trong lúc gọi AI/OCR/HTTP hoặc chạy tác vụ dài. Ưu tiên lưu trạng thái bằng transaction ngắn, thực thi ngoài transaction, rồi lưu kết quả.
- Persist trạng thái lỗi đúng cách; xét concurrency, retry/idempotency và dữ liệu nguồn thay đổi trước khi ghi kết quả.
- Không dựa vào self-invocation để kích hoạt `@Transactional`; không mang lazy entity qua tác vụ nền.
- Nếu cần executor, giới hạn concurrency/queue và có trạng thái persist. Không tạo thread không giới hạn.
- Giữ Flyway quản lý DB, production dùng `ddl-auto=validate`. Không sửa migration đã áp dụng; thêm migration tương thích khi cần.
- Chỉ thêm index dựa trên query thực tế. Danh sách tăng lớn cần pagination và giới hạn page size.
- Xác minh DB đích trước khi chạy ứng dụng, migration hoặc test. Không vô tình ghi cloud bằng cấu hình local trỏ tới cloud.

## Frontend và contract xuyên suốt

- Khi sửa API/contract, kiểm tra consumer frontend và cập nhật đồng bộ nếu cần.
- UI điều chỉnh tham số theo schema được hỗ trợ, có units/ranges/steps và validation nhất quán với backend.
- Dùng scene binding/asset registry hiện có. Chỉ thêm renderer chuyên biệt khi có khác biệt vật lý hoặc biểu diễn cần thiết.
- Giữ loading/empty/error/conflict states rõ ràng. Không che lỗi bằng dữ liệu mẫu hoặc thông báo thành công giả.

## Kiểm thử và bằng chứng

- Chọn test theo rủi ro thay đổi. Refactor solver/schema/persistence cần chứng minh kết quả, contract và replay giữ đúng; sửa policy cần kiểm tra cả allow và deny.
- Mock external AI trong regression để kết quả ổn định; test thật khi nhiệm vụ yêu cầu và cấu hình phù hợp.
- Backend: chạy test liên quan và `./mvnw.cmd test` cho thay đổi đáng kể. Frontend: chạy `npm run check` nếu sửa frontend hoặc contract ảnh hưởng frontend.
- Không sửa expected result chỉ để test xanh khi chưa chứng minh nghiệp vụ mới đúng.
- Docker không có hoặc integration test bị skip phải ghi rõ. Compile thành công không chứng minh đủ phân quyền hoặc tính đúng vật lý.
- Báo cáo chính xác phần đã sửa, bằng chứng kiểm thử, phần chưa kiểm chứng và giới hạn. Không tuyên bố “100% production” chỉ vì test hiện có pass.

## Workspace, secrets và phạm vi hành động

- Kiểm tra git status, branch và thay đổi của người dùng trước khi sửa; không ghi đè công việc ngoài phạm vi nhiệm vụ.
- Tuyệt đối không xóa/ghi đè `.env.local`. Chỉ thêm biến khi được yêu cầu và giữ các giá trị hiện có; không in secret ra log, báo cáo hoặc commit.
- Không tự reset/clean/xóa branch, dữ liệu hay file hàng loạt để “dọn code”. Xác minh dependency và phạm vi trước khi xóa.
- Chỉ commit, push, deploy hoặc ghi cloud khi được người dùng cho phép trong phạm vi công việc tương ứng.
- Với yêu cầu đánh giá: đưa bằng chứng và đề xuất, không tự triển khai. Với yêu cầu triển khai: sửa hoàn chỉnh, kiểm thử phù hợp và bàn giao trung thực.
