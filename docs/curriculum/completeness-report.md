# Bao phu mo phong Vat li THPT 10-12

Ngay kiem tra: 2026-09-20.

Ma tran hien tai co 85 bai trong catalog va 90 dong coverage (bao gom cac
dong tong hop, thuc hanh va pilot). Script `scripts/check-curriculum-coverage.mjs`
kiem tra ca hai chieu catalog -> coverage va coverage -> catalog cho cac dong
`curriculum.*`/`practical.*`.

| Lop | So bai catalog | Coverage rows | Trang thai test solver/reference |
| --- | ---: | ---: | --- |
| 10 | 29 | 31 | 31/31 tested |
| 11 | 27 | 29 | 29/29 tested |
| 12 | 29 | 30 | 30/30 tested |

Tong ket: 85/85 bai catalog duoc mapping; 90/90 coverage rows co model,
template, activity, test registry va source locator tu dong. Trong do 64 dong
duoc ghep vao ba bo sach KNTT do Vietnhb cung cap, 26 dong dung khung chuong
trinh Thong tu 32 lam fallback vi ba sach nay khong co bai tuong ung.
`approved` hien co 90/90 theo owner-attestation cua Vietnhb sau khi doi chieu
nguon va test. Day la approval noi bo co audit trail; independent academic
review van la mot cong rieng neu can chung nhan ben ngoai.

Bao cao theo topic duoc tinh tu `scripts/report-topic-coverage.mjs` (lesson
mapping, khong dem so schema duy nhat tren moi lesson):

| Topic | Lessons | Schemas | Mapped | Approved | Coverage |
| --- | ---: | ---: | ---: | ---: | ---: |
| KINEMATICS | 12 | 5 | 12 | 12 | 100% |
| DYNAMICS | 15 | 11 | 15 | 15 | 100% |
| CIRCUITS | 10 | 13 | 10 | 10 | 100% |
| WAVES | 9 | 10 | 9 | 9 | 100% |
| THERMAL | 7 | 9 | 7 | 7 | 100% |
| OPTICS | 5 | 7 | 5 | 5 | 100% |
| ELECTROMAGNETISM | 8 | 4 | 8 | 8 | 100% |
| MODERN_PHYSICS | 12 | 12 | 12 | 12 | 100% |
| PRACTICAL_AND_DATA | 7 | 3 | 7 | 7 | 100% |
| **TOTAL** | **85** | **74** | **85** | **85** | **100%** |

Mot lesson co the dung chung mot schema family, va mot lesson tong hop co the
co nhieu model/schema; vi vay khong the chia `so schema / so lesson` de suy ra
coverage. Bang 66 lessons/33 schemas la ma tran cu, khong phai catalog hien tai.

Bo sung uu tien cao da co contract rieng:

- `kinematics_position_time_graph`, `kinematics_velocity_time_graph`,
  `kinematics_acceleration_time_graph`;
- `experimental_data_graph` cho do thi du lieu thi nghiem dang fit tuyen tinh;
- `ohms_law`, `resistors_series`, `resistors_parallel` duoc phan topic
  `CIRCUITS` (khong con gan nham vao `ELECTROMAGNETISM`).
- `damped_forced_oscillation`, `moment_equilibrium`, `phase_change`,
  `xray_imaging`, `ct_reconstruction`, `mri_relaxation`,
  `de_broglie_diffraction`, `sensor_op_amp` va `radio_signal_chain` da co
  solver/reference va golden test.
- `source_internal_resistance`, `energy_band_transition` va
  `nuclear_reaction_energy` da bo sung cho cac outcome ve noi tro, vung nang
  luong va can bang khoi luong phan ung hat nhan.
- `linear_drag_motion`, `uniform_electric_field` va `thermistor_response` da
  bo sung cho can khong khi, dien truong deu va cam bien nhiet.

Day la coverage noi bo da duoc Vietnhb owner-attest, khong phai chung nhan doc
lap cua co quan/chuyen gia ben ngoai. Chuong trinh van can doi chieu theo tung
edition sach giao khoa va page/section locator khi phat hanh ban co kiem dinh
hoc thuat doc lap.

Đối chiếu chi tiết với chương trình chính thức trong PDF đính kèm được ghi tại
[official-program-gap-audit.md](official-program-gap-audit.md). Báo cáo đó kết
luận rõ: gate nội bộ 100% không đồng nghĩa 100% mô phỏng cho toàn bộ nội dung
chính khóa và các chuyên đề 10.1–12.3.

Lenh kiem tra:

```powershell
cd physLive_preview
node scripts/attach-curriculum-sources.mjs
node scripts/check-curriculum-coverage.mjs
node scripts/report-topic-coverage.mjs
cd backend; mvn test -q
cd ..\react-client; npm run check
```
