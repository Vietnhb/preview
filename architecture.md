# Kiến trúc luồng tạo và xác minh mô phỏng PhysLive

## Mục tiêu

LLM hiểu mô tả tự do và tạo cách trình bày trực quan theo ngữ cảnh. Hệ thống xác minh vật lý dựa trên công thức chuẩn trong topic schema do reviewer quản lý, không dựa trên việc mã vẽ chạy thành công.

Tài liệu này mô tả kiến trúc mong muốn, không khẳng định luồng hiện tại đã triển khai đầy đủ và không quy định cách triển khai.

## Luồng tổng thể

Mô tả của user → JEV xác định schema phù hợp → LLM nhận mô tả và schema → LLM trả đầy đủ đặc tả mô phỏng → kiểm tra dữ kiện → thực thi và xác minh vật lý → gắn kết quả vật lý với phần hiển thị → render → lưu mô phỏng.

Việc xác minh vật lý diễn ra sau khi LLM trả đặc tả đầy đủ. Chọn schema trước không đồng nghĩa với tính hoặc xác minh vật lý trước khi LLM hiểu đề bài.

## 1. Mô tả của user

User có thể mô tả tự do về vật thể, số lượng, chuyển động, tương tác và cách hiển thị mong muốn.

Hệ thống giữ nguyên những số liệu, tên gọi, số lượng và quan hệ được nêu rõ. Không giới hạn đề bài vào một loại vật thể, một cảnh mẫu hoặc một bộ asset cố định.

## 2. JEV xác định schema

JEV xác định topic schema phù hợp với ngữ cảnh vật lý của mô tả. Schema được chọn cùng phiên bản của nó trở thành căn cứ xuyên suốt lần tạo mô phỏng.

LLM sử dụng schema đã được xác định, không tự tạo topic mới hoặc thay thế schema đã chọn. Nếu chưa xác định được schema đủ tin cậy, hệ thống làm rõ yêu cầu hoặc chuyển sang minh họa chưa xác minh khi phù hợp.

Việc xác định schema thuộc luồng tạo mô phỏng, không đợi giáo viên bấm lưu bài mới thực hiện.

## 3. LLM tạo đặc tả đầy đủ

LLM nhận mô tả gốc và schema phù hợp, sau đó trả đặc tả gồm:

- Các vật thể và số lượng được mô tả.
- Dữ kiện, đơn vị, trạng thái ban đầu và thời gian mô phỏng.
- Quan hệ, tương tác và điều kiện kết thúc.
- Các giả định và biểu thức suy ra cần thiết.
- Ý tưởng hiển thị, hình dạng, bố cục, bối cảnh và hiệu ứng.

LLM được bổ sung chi tiết trực quan khi user không chỉ định, nhưng không được tự thay đổi dữ kiện vật lý đã nêu. Chỉ hỏi lại khi dữ kiện thiếu làm thay đổi bản chất hoặc kết quả vật lý; không bắt buộc kiểm kê mọi chi tiết trang trí của cảnh.

## 4. Kiểm tra dữ kiện trước khi tính

Sau khi nhận đặc tả đầy đủ, backend kiểm tra dữ kiện cần thiết, đơn vị, tính nhất quán và sự phù hợp với schema đã chọn.

Những giả định ảnh hưởng đến kết quả phải được thể hiện rõ. Trường hợp cần thêm dữ kiện sẽ quay lại bước làm rõ, không được coi là đã xác minh.

## 5. Công thức chuẩn và thực thi vật lý

Topic schema là nguồn định nghĩa vật lý chuẩn do reviewer quản lý. Một topic có thể chứa nhiều capability và nhiều dạng công thức, không tạo schema mới cho từng vật thể hoặc từng mô tả user.

Bộ máy toán tổng quát sử dụng các định nghĩa trong schema để tính kết quả. Không bổ sung nhánh xử lý riêng theo câu chữ, số lượng vật thể hoặc cảnh cụ thể như “hai xe” hay “năm con lắc”.

Các công thức được phân biệt theo vai trò:

- Công thức chuẩn: nằm trong topic schema đã được phê duyệt.
- Biểu thức suy ra do LLM đề xuất: chỉ được dùng làm vật lý khi ánh xạ và kiểm chứng được với định nghĩa chuẩn.
- Công thức trang trí: phục vụ hiển thị, không thay đổi kết quả vật lý.
- Công thức chưa ánh xạ được: không đủ căn cứ để xác nhận vật lý.

## 6. Numerical solver và closed-form

Capability xác định phương pháp tính và phương pháp xác minh phù hợp; không bắt buộc mọi bài đều có nghiệm closed-form.

- Có numerical và closed-form: so sánh kết quả tại nhiều thời điểm theo sai số cho phép.
- Chỉ có numerical: xác minh bằng bằng chứng hội tụ, residual, bất biến và các trường hợp chuẩn phù hợp.
- Closed-form là phương pháp chính: tạo dữ liệu theo thời gian từ nghiệm và kiểm tra theo hợp đồng xác minh của schema.
- Không có định nghĩa vật lý phù hợp: chỉ minh họa chưa xác minh nếu vẫn biểu diễn trung thực được.

Hai phương pháp cho kết quả giống nhau chưa đủ chứng minh đã hiểu đúng đề bài; dữ kiện, giả định và phạm vi áp dụng cũng phải phù hợp.

## 7. Kết quả xác minh

Chỉ backend có quyền xác nhận vật lý. Báo cáo gồm phương pháp tính, phương pháp xác minh, công thức chuẩn, giả định, phiên bản schema/solver, sai số, bằng chứng và cảnh báo liên quan.

Các trạng thái được phân biệt rõ:

- `UNDERSTOOD`: đã hiểu yêu cầu, chưa xác nhận vật lý.
- `NEEDS_CLARIFICATION`: cần bổ sung dữ kiện vật lý.
- `ASSUMPTION_REVIEW`: cần xem xét giả định ảnh hưởng đến kết quả.
- `VERIFIED_ANALYTICAL`: đã xác minh theo nghiệm giải tích tham chiếu.
- `VERIFIED_NUMERICAL`: đã xác minh theo hợp đồng numerical.
- `VISUAL_ONLY_UNVERIFIED`: chỉ minh họa, chưa xác minh vật lý.
- `UNSUPPORTED`: chưa thể biểu diễn trung thực yêu cầu.
- `FLAGGED`: phát hiện vấn đề cần xem xét, không được coi là đã xác minh.

Không chuyển trạng thái chưa xác minh thành `OK` hoặc `VERIFIED` chỉ vì render thành công.

## 8. Render mô phỏng

LLM quyết định cách vẽ theo mô tả và ngữ cảnh, không bị khóa vào canvas đen, một asset hoặc một bố cục. Nền hiển thị phù hợp với theme workspace của user.

Trong chế độ solver-bound, chuyển động và các đại lượng vật lý đến từ kết quả backend. Renderer chỉ trình bày dữ liệu đó, không tự thay thế bằng công thức vật lý do LLM viết trong mã hiển thị.

Trong chế độ visual-only, có thể dùng hoạt cảnh do AI tạo nhưng luôn hiển thị trạng thái chưa xác minh.

Sandbox kiểm tra an toàn và lỗi render, không xác nhận tính đúng đắn vật lý. Báo cáo frontend không có quyền nâng trạng thái xác minh.

## 9. Thông tin hiển thị cho user

User thấy mô phỏng cùng định luật chuẩn, biểu thức suy ra đã được ánh xạ, giả định, phương pháp tính, phương pháp xác minh, phiên bản và sai số.

Công thức trang trí không được trình bày như công thức vật lý. Nếu chưa xác minh hoặc không hỗ trợ, giao diện phải thể hiện rõ giới hạn đó.

## 10. Reviewer và phiên bản

Reviewer quản lý nội dung vật lý của topic: dữ kiện, đơn vị, công thức, phạm vi áp dụng, phương pháp tính, điều kiện xác minh và đầu ra.

Reviewer không quản lý core API, authentication, sandbox hoặc giới hạn bảo mật. Phiên bản đã publish là bất biến; thay đổi nội dung tạo phiên bản mới, có lịch sử chỉnh sửa và phê duyệt.

## 11. Lưu và phát lại

Khi lưu bài, hệ thống lưu đặc tả, kết quả vật lý, báo cáo xác minh, phần hiển thị và các phiên bản liên quan. Bước lưu không chọn lại schema hoặc âm thầm thay đổi kết quả đã tạo.

Mô phỏng cũ gắn với đúng phiên bản đã dùng để tránh thay đổi nội dung khi topic được cập nhật.

## Nguyên tắc xuyên suốt

Mô tả mở thuộc trách nhiệm hiểu và trình bày của LLM. Công thức chuẩn thuộc schema do reviewer quản lý. Tính toán và xác minh thuộc backend. Hiển thị thuộc renderer. An toàn thực thi thuộc sandbox.

Render đẹp, chạy được và đúng vật lý là ba tiêu chí riêng biệt; không tiêu chí nào tự động thay thế tiêu chí còn lại.
