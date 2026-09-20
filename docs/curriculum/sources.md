# Nguon kiem ke chuong trinh va mo hinh

Ngay kiem tra: 2026-09-20. Pham vi chuan la Vat li THPT Viet Nam lop 10-12,
bao gom noi dung cot loi, thuc hanh va chuyen de. Danh muc SGK va phu luc mon
Vat li da duoc gan vao coverage theo source catalog. Vietnhb da owner-attest
toan bo rows; independent academic certification van la mot muc tieu rieng.

| ID | Nguon | Pham vi su dung | Trang thai |
| --- | --- | --- | --- |
| `src.moet.tt32` | [Thong tu 32/2018/TT-BGDDT](https://vbpl.moj.gov.vn/bogiaoducdaotao/Pages/vbpq-toanvan.aspx?ItemID=146721) | Can cu chuong trinh GDPT 2018; can trich rieng phu luc mon Vat li | `unverified` - da ghi nhan URL, chua dong edition/locator mon hoc trong repo |
| `src.moet.vbhn` | [Van ban hop nhat chuong trinh tong the](https://moet.gov.vn/content/vanban/Lists/VBPQ/Attachments/1483/vbhn-chuong-trinh-tong-the.pdf) | Boi canh cau truc lop/chuyen de | `unverified` - khong thay the phu luc mon Vat li |
| `src.openstax.waves.16.2` | [OpenStax University Physics, section 16.2](https://openstax.org/books/university-physics-volume-1/pages/16-2-mathematics-of-waves) | Doi chieu cong thuc song dieu hoa, pha, k, omega, c=f lambda | `verified` cho lap luan mo hinh; khong phai nguon xac nhan chuong trinh Viet Nam |
| `src.openstax.waves.16.3` | [OpenStax University Physics, section 16.3](https://openstax.org/books/university-physics-volume-1/pages/16-3-wave-speed-on-a-stretched-string) | Doi chieu `c=sqrt(F/mu)` voi luc cang va khoi luong rieng dai | `verified` cho mo hinh ly tuong |
| `src.repo.schema_catalog` | `backend/src/main/resources/schemas/catalog.json` | 74 versioned schemas covering mechanics, waves, thermal, optics, electromagnetism, modern physics, medical imaging and practical/data work | `implemented` noi bo; khong phai nguon giao trinh |
| `src.repo.curriculum_catalog` | `backend/src/main/resources/curriculum/catalog.json` | Bootstrap topic/module/lesson inventory with 85 lesson slugs for grades 10-12 | `implemented` noi bo; chua du ma tran nguon chinh thuc |
| `src.sgk.vatli10.kntt` | [Vật lí 10 - Kết nối tri thức (PDF supplied by Vietnhb)](https://sqhx-hanoi.mediacdn.vn/91579363132710912/2025/11/9/sgk-vat-ly-lop-10-1762705900568218724925.pdf) | Kinematics, dynamics, energy, momentum, circular motion, deformation and measurement lessons | `publisher-cover` - locator is generated from canonical chapter/lesson keys |
| `src.sgk.vatli11.kntt` | [Vật lí 11 - Kết nối tri thức (PDF supplied by Vietnhb)](https://sqhx-hanoi.mediacdn.vn/91579363132710912/2025/11/18/3-sgk-vl-11-kntt-17634527300041496836870.pdf) | Oscillations, waves, electric field and direct-current circuit lessons | `publisher-cover` - locator is generated from canonical chapter/lesson keys |
| `src.sgk.vatli12.kntt` | [Vật lí 12 - Kết nối tri thức (mirror supplied by Vietnhb)](https://sieugioi.com/docs/threads/sach-giao-khoa-vat-li-12-file-pdf.1071/) | Thermal physics, ideal gas, magnetic field/induction and nuclear physics lessons | `secondary-mirror` - publisher metadata requires independent verification |

Source attachment is reproducible with `node scripts/attach-curriculum-sources.mjs`. It writes a stable `source:<id>;chapter:<...>;lesson:<...>` locator for every coverage row. Rows not matched to a supplied textbook are attached to the official curriculum framework as `moet_tt32-fallback` and remain `tested` until a textbook locator is independently confirmed.

Khong sao chep nguyen van sach vao san pham. Moi dong coverage phai gan
nguon, locator, edition va nguoi duyet khi co tai lieu chinh thuc tuong ung.
