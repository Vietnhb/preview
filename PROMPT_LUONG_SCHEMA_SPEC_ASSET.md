# Prompt: Hoàn thiện luồng schema → spec → asset → mô phỏng

Bạn là AI triển khai trong repo PhysLive. Sửa luồng hiện có theo đúng các tầng dưới đây. Làm xong tầng nào, chạy kiểm thử tầng đó rồi đổi `[ ]` thành `[x]` ngay trong file này. Chỉ đánh dấu khi có bằng chứng; nếu lỗi, giữ `[ ]` và ghi một dòng nguyên nhân.

## Tầng 1 — Chọn schema

- [x] JEV chọn schema đã được phê duyệt từ mô tả user; kết quả gồm `schemaId`, version và mức tin cậy. Chưa chọn asset ở tầng này.
- [x] Nếu không có schema phù hợp hoặc độ tin cậy thấp, xin lỗi user chưa hỗ trợ cho mô tả này.

## Tầng 2 — Hoàn thiện spec vật lý

- [x] LLM nhận mô tả user và schema đã chọn, tạo spec đúng contract; giữ nguyên mọi vật thể, số lượng, quan hệ và giá trị đã nêu.
- [x] Kiểm tra số vật thể, giới hạn schema, quantity bắt buộc, đơn vị và điều kiện kết thúc. Mâu thuẫn hoặc thiếu dữ liệu thì hỏi đúng phần đó, nhận câu trả lời và kiểm tra lại cho đến khi đủ.
- [x] Không tự thêm giá trị mặc định, gộp/bỏ vật thể hay giảm phạm vi mô phỏng nếu user chưa đồng ý rõ ràng mọi thứ đều do user comfirm nếu thấy mơ hồ không rõ.

## Tầng 3 — Tìm asset

- [x] Khi spec đã đủ và được xác nhận, LLM tạo tóm tắt ngắn từ spec+ mô tả của User: số asset, loại, đặc điểm nhận diện và liên kết tới từng `objectId`. Tóm tắt không thay đổi spec vật lý.
- [x] Backend gửi tóm tắt ngắn đó cho JEV để tìm trong catalog asset hiện có. JEV chỉ trả ID asset có trong catalog, loại phù hợp, mức khớp và độ tin cậy; không tạo asset hoặc sửa spec, nếu không có tìm asset gần giống cũng được mà đưa độ tin cậy thấp cho LLM để LLM xác nhận với có được không .
- [x] Gắn mỗi asset vào đúng render target và `objectId` đã có. Bước gắn asset chỉ được thêm visual binding; schema, objects, quantities, relations và end condition phải giữ nguyên.

## Tầng 4 — Quyết định của user

- [x] Asset khớp chính xác và đạt ngưỡng tin cậy được dùng trực tiếp.
- [x] Nếu chỉ có asset gần giống, trình bày tên asset và điểm khác biệt cụ thể; chỉ dùng sau khi user đồng ý rõ ràng.
- [x] Nếu user từ chối hoặc không có asset phù hợp, dừng tạo mô phỏng và giữ nguyên spec; không tự dùng hình thay thế.

## Tầng 5 — Kiểm thử luồng thật

- [x] Test BE: đề một vật, `v0=10 m/s`, `a=2 m/s²`, thời gian `8 s`; hỏi `initial_position`, trả lời `0`, rồi xác nhận asset và tạo mô phỏng.
  - HTTP thật: create `201`, extract `200`, confirm `200`, accept substitute `200`, create simulation `201`; validation passed, `x(0)=0 m`, `x(8)=144 m`, `v(8)=26 m/s`, kết thúc tại `8 s`. User đồng ý `block-amber`.
- [ ] Test BE: đề yêu cầu 2 vật nhưng schema chỉ hỗ trợ 1; phải hỏi về mâu thuẫn trước bước tìm asset.
  - E2E FE chưa đạt: JEV phát hiện capacity 2>1 nhưng LLM không trả spec hợp lệ; lần 1 options ambiguity ngoài candidate schema, lần 2 thiếu `initial_position`. Chưa hỏi xác nhận, chưa tìm asset.
- [x] Test BE: asset exact, substitute được chấp nhận, substitute bị từ chối, và asset không tồn tại. Không trường hợp nào được tạo mô phỏng sai spec.
- [ ] Test frontend và build. Ghi status HTTP và lỗi gốc của bước hỏng; không gom mọi lỗi thành một thông báo khó truy vết.
  - Build và lint qua; npm test 39/40 do thiếu `scripts/generate-schema-catalog.mjs`. E2E FE extract kết thúc HTTP 502 vì hai lần LLM output sai contract (xem case 2 vật).

Khi kết thúc, báo tối đa 5 dòng: tầng đã hoàn thành, test đã chạy và kết quả, blocker còn lại. Không báo hoàn thành nếu chưa chạy được luồng thật. Jev trong project chỉ có 1 nhiệm vụ duy nhất là phân loại
