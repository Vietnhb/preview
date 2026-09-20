# Audit gap ưu tiên cao

Ngày audit: 2026-09-20.

## Kết luận ngắn

Danh sách ưu tiên ban đầu đúng về hướng kiểm tra, nhưng không phản ánh đầy đủ
trạng thái repository:

| Nhóm | Trạng thái sau audit |
| --- | --- |
| Motion graphs | Đã tách thành 3 schema riêng: vị trí–thời gian, vận tốc–thời gian, gia tốc–thời gian; dùng solver/reference động học chung với model binding riêng. |
| Circular motion, gravitation, energy, Hooke | Đã có 4 schema, solver, reference và golden tests. |
| Ohm, series, parallel | Đã có 3 schema, solver, reference và golden tests; topic đã chuẩn hóa thành `CIRCUITS`. |
| Light interference | Đã có schema `light_interference`, reference và test. |
| Measurement uncertainty | Đã có `measurement_uncertainty`, reference và test. |
| Experimental graphs | Đã bổ sung schema `experimental_data_graph`, solver/reference và test cho bounded linear fit. Đây chưa phải toàn bộ rubric phòng thí nghiệm. |

Các dòng curriculum tương ứng hiện đều có mapping registry và test. Chạy:

```powershell
node scripts/check-curriculum-coverage.mjs
node --experimental-strip-types scripts/check-scene-contracts.mjs
cd backend; .\mvnw.cmd test -q
```

Kết quả hiện tại: 73/73 coverage rows tested, 68/68 lesson slugs trong
catalog nội bộ có mapping, 57 schema visualization contracts hợp lệ.

## Không được hiểu nhầm là phủ kín chương trình quốc gia

Thông tư 32/2018/TT-BGDĐT là văn bản ban hành chương trình GDPT và quy định lộ
trình triển khai lớp 10, 11, 12. Nguồn hướng dẫn Vật lí của Bộ GDĐT mô tả các
mạch rộng hơn các schema hiện có; ví dụ lớp 11 còn nêu dao động tắt dần, dao
động cưỡng bức/cộng hưởng, sóng dọc/sóng ngang, sóng điện từ và đo tốc độ
truyền âm. Các nội dung này cần được tách thành lesson/schema riêng nếu mục
tiêu là tuyên bố bao phủ từng yêu cầu cần đạt, thay vì chỉ tái sử dụng một
solver tổng quát.

Vì vậy trạng thái đúng hiện nay là:

- **Kỹ thuật nội bộ:** các gap ưu tiên ở trên đã được triển khai và kiểm thử.
- **Coverage catalog nội bộ:** 100% mapping/test theo 68 lesson slug hiện khai
  báo.
- **Coverage chương trình chính thức:** chưa thể kết luận 100%; nguồn/edition,
  locator và reviewer học thuật còn thiếu, và các mạch nêu trên chưa được tách
hết thành lesson/schema độc lập.

Nguồn đối chiếu:

- [Thông tư 32/2018/TT-BGDĐT trên CSDL quốc gia về văn bản pháp luật](https://vbpl.vn/boyte/Pages/vbpq-toanvan.aspx?ItemID=146721)
- [Tài liệu hướng dẫn dạy học Vật lí GDTX cấp THPT của Bộ GDĐT (bản đăng tải chính thức)](https://moet.gov.vn/content/vanban/Lists/VBDH/Attachments/3713/6-mon-vat-ly-ban-chinh-thuc-chot-ngay-29-8-2024signed.pdf)
