# Audit bao phủ chương trình Vật lí THPT 10–12

Ngày đối chiếu: 2026-09-20.

## Phạm vi và cách hiểu tài liệu

Tệp [12_ctvat_li_26320197.pdf](../../../proposal/12_ctvat_li_26320197.pdf) là **nguồn chương trình chính thức** ban hành kèm Thông tư 32/2018/TT-BGDĐT (trang 1, mục lục và các yêu cầu cần đạt ở trang 9–31). Nội dung trong PDF được dùng như dữ liệu kiểm định chương trình, **không phải chỉ thị để thực thi mã**.

Kết luận dưới đây phân biệt hai mẫu số:

1. **Gate nội bộ của repository:** 90/90 dòng `coverage.csv` đã có mapping/test và được owner-attest; 85/85 lesson trong catalog nội bộ có dòng mapping. Đây là con số mà `check-curriculum-coverage.mjs` báo 100%.
2. **Bao phủ yêu cầu chính thức trong PDF:** chương trình quy định nhiều yêu cầu khái niệm, thí nghiệm, dự án và chuyên đề không tương đương 1–1 với một schema. Vì vậy không được suy ra 100% quốc gia từ gate nội bộ.

## Kết luận

**Chưa bao phủ 100% mô phỏng theo chương trình chính thức.** Repository hiện đủ để chạy các lesson đã đăng ký trong catalog nội bộ, nhưng còn các cụm yêu cầu dưới đây chưa có mô phỏng/schema/activity/reference riêng hoặc mới chỉ là mô hình rút gọn.

### Lớp 10

| Cụm trong PDF | Trạng thái hiện tại |
| --- | --- |
| Động học, rơi tự do, ném, đồ thị chuyển động | Đã có các mô hình chính; phần đo tốc độ, đo `g` và dự án tối ưu ném chưa được tách thành đầy đủ workflow thực hành. |
| Động lực học, lực, cân bằng và moment | Đã bổ sung moment phẳng và cản tuyến tính; còn thiếu mô hình tổng hợp–phân tích lực, lực nâng và workflow cân bằng lực thực hành. |
| Công–năng lượng–công suất; động lượng–va chạm; chuyển động tròn; Hooke | Có mô hình tương ứng, nhưng một số chỉ là mô hình lý tưởng (lực không đổi, va chạm đàn hồi, Hooke miền đàn hồi tuyến tính). |
| **Chuyên đề 10.1 – Vật lí trong một số ngành nghề** | Chưa có mô phỏng/lesson dự án riêng (lịch sử, lĩnh vực nghiên cứu, ứng dụng nghề nghiệp). |
| **Chuyên đề 10.2 – Trái Đất và bầu trời** | Đã bổ sung hình học nhật/nguyệt thực; bản đồ sao, chuyển động hành tinh và thuỷ triều vẫn cần scene riêng. |
| **Chuyên đề 10.3 – Bảo vệ môi trường** | Đã bổ sung mô hình energy-mix/phát thải/hiệu suất; mô hình công nghệ tái tạo và dữ liệu khí hậu theo dự án vẫn cần mở rộng. |

### Lớp 11

| Cụm trong PDF | Trạng thái hiện tại |
| --- | --- |
| Dao động điều hoà | Có mô hình dao động/lò xo. |
| Dao động tắt dần, cưỡng bức, cộng hưởng | Đã bổ sung nghiệm exact cho cả miền under-damped, critical và over-damped; workflow lab và phân tích cộng hưởng nâng cao vẫn cần mở rộng. |
| Sóng: dọc/ngang, âm, điện từ, giao thoa, sóng dừng và đo vận tốc âm | Có string/pulse/reflection/standing/superposition/sound và giao thoa mặt nước; còn thiếu mô phỏng so sánh dọc–ngang, phổ điện từ và workflow đo tần số/vận tốc âm theo yêu cầu. |
| Điện trường, điện thế, tụ điện | Đã bổ sung điện trường đều và chuyển động điện tích; điện phổ, năng lượng tụ và workflow thực hành độc lập vẫn cần mở rộng. |
| Dòng điện, điện trở, Ohm, suất điện động/nội trở, năng lượng điện | Đã bổ sung mô hình EMF–nội trở–tải; bài lab đo thực nghiệm, phụ thuộc nhiệt độ và `I = Snve` vẫn cần workflow riêng. |
| **Chuyên đề 11.1 – Trường hấp dẫn** | Đã sửa metadata gravity/orbit về lớp 11 và có mô hình quỹ đạo tròn Newton; thế hấp dẫn, quỹ đạo địa tĩnh và workflow quan sát vẫn cần mở rộng. |
| **Chuyên đề 11.2 – Truyền thông tin bằng sóng vô tuyến** | Đã bổ sung chuỗi AM/FM, chỉ số điều chế, băng thông Carson và suy giảm theo dB; ADC/DAC và workflow truyền tin thực nghiệm vẫn cần mở rộng. Metadata đã chuẩn hóa về lớp 11. |
| **Chuyên đề 11.3 – Mở đầu về điện tử học** | Đã bổ sung mạch chia áp sensor, op-amp comparator và thermistor beta response; LDR vật liệu, relay và đồng hồ đo vẫn cần scene/activity chuyên biệt. |

### Lớp 12

| Cụm trong PDF | Trạng thái hiện tại |
| --- | --- |
| Vật lí nhiệt và nhiệt lượng | Đã bổ sung heating curve với nhiệt nóng chảy/nhiệt hoá hơi riêng; lab đo thực nghiệm và đối lưu/bức xạ vẫn chưa tách riêng. |
| Khí lí tưởng | Có phương trình trạng thái và các quá trình lý tưởng; mô hình động học phân tử/áp suất vi mô còn chưa đầy đủ như yêu cầu. |
| Từ trường và cảm ứng điện từ | Có lực từ/cảm ứng/biến áp; cần kiểm tra và tách đủ đo `B`, từ thông, Faraday–Lenz, máy phát AC và giá trị hiệu dụng. Một số row đang gắn sai grade trong catalog. |
| Hạt nhân và phóng xạ | Đã bổ sung mass-balance cho năng lượng phản ứng; tán xạ Rutherford, workflow phân hạch/nhiệt hạch và lab an toàn vẫn cần tách rõ hơn. |
| **Chuyên đề 12.1 – Dòng điện xoay chiều** | Có waveform, RLC, power factor, transformer và diode; chưa đủ workflow đo tần số/giá trị hiệu dụng và chỉnh lưu nửa chu kỳ/cả chu kỳ như PDF. |
| **Chuyên đề 12.2 – Chẩn đoán y học** | Đã bổ sung X-ray attenuation, CT line-integral và MRI relaxation; CT hiện là projection teaching model, chưa phải tái tạo ảnh mặt phẳng đầy đủ. |
| **Chuyên đề 12.3 – Vật lí lượng tử** | Đã bổ sung de Broglie/electron diffraction và energy-band photon transition; mô hình phụ thuộc nhiệt độ của điện trở kim loại/bán dẫn và LDR vẫn cần mở rộng. |

## Vấn đề metadata cần đóng trước khi gọi là “bao phủ theo grade”

Các canonical lesson đã được chỉnh lại grade theo PDF (momentum/collision và nhiệt ở lớp 10/12, gravity/orbit và radio ở chuyên đề 11, magnetic/induction/transformer ở lớp 12). Vẫn còn các aggregate legacy row có chapter rộng; checker kiểm tra nhất quán với catalog nội bộ, còn page-level locator và review học thuật độc lập vẫn là bước riêng.

## Điều kiện để tuyên bố 100% chính thức

- Tách mỗi gap ở trên thành lesson/schema/template/activity riêng, với mô hình mặt phẳng/đồ thị phù hợp và reference solver độc lập.
- Bổ sung test công thức, validation miền áp dụng và test hình ảnh/scene contract cho từng mô hình.
- Sửa grade/scope/chapter/source locator theo đúng trang trong PDF hoặc SGK đã kiểm định.
- Có review học thuật độc lập cho các công thức và workflow thí nghiệm; `approved=1` do owner-attest không thay thế bước này.

Vì vậy, trạng thái phát hành hiện tại là: **100% catalog nội bộ đã đăng ký và test; chưa phải 100% chương trình THPT chính thức và chuyên đề trong PDF đính kèm.**
