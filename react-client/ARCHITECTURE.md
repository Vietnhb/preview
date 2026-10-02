# Kiến trúc frontend PhysLive

## Tổ chức theo nghiệp vụ

Frontend tham khảo cách chia API, trang, layout và component của `StarterProject202603/frontend`. PhysLive nhóm các phần của cùng nghiệp vụ trong `features`, sử dụng React composition và custom hook để giữ trách nhiệm rõ. Axios, Radix, Motion và Zustand đang có tiếp tục phục vụ giao diện; cấu trúc không yêu cầu sao chép bộ generic CRUD, Redux, registry hoặc dependency của template.

```text
src/
├── main.tsx
├── app/                         # Shell, route, bootstrap, access, realtime
├── config/                      # Cấu hình môi trường frontend
├── shared/
│   ├── api/                     # Client HTTP dùng chung
│   ├── auth/                    # User, role, session store
│   ├── effects/                 # Hiệu ứng dùng ở nhiều màn hình
│   ├── hooks/                   # Hook nền dùng chung
│   ├── layout/                  # Navbar, navigation, page container
│   ├── lib/                     # Token, JWT, lỗi HTTP
│   ├── types/                   # Hợp đồng dữ liệu dùng ở nhiều nghiệp vụ
│   └── ui/                      # Thành phần giao diện dùng chung
├── features/
│   ├── account/
│   ├── assignments/
│   ├── auth/
│   ├── billing/
│   ├── curriculum/
│   ├── library/
│   ├── management/
│   ├── public/
│   ├── reviewer/
│   ├── school/
│   │   └── dataio/              # Nhập CSV, xem trước, sửa dòng, tải mẫu
│   ├── simulation/
│   │   └── engine/
│   │       ├── runtime/         # Dữ liệu theo thời gian và playback
│   │       ├── scene/           # Biên dịch và ràng buộc scene graph
│   │       └── renderer/        # Vẽ theo capability
│   └── support/
└── styles/                      # CSS nền được nạp một lần
```

Các thư mục gốc `pages`, `components`, `api`, `types`, `utils` và `store` cũ đã chuyển vào phần dùng chung hoặc nghiệp vụ tương ứng. Tên role dùng để kiểm tra quyền và điều hướng; nghiệp vụ quyết định nơi đặt code. Ví dụ `school/components/SchoolClasses` phục vụ cả SCHOOL và STAFF trưởng bộ môn, còn duyệt thư viện cấp trường thuộc `library`.

## Trách nhiệm bên trong feature

Chỉ tạo các thư mục cần dùng; feature không phải có đủ mọi tầng.

- `pages/`: màn hình được route mở, ghép layout, hook và component; giữ các quyết định hiển thị của màn hình.
- `components/`: panel, form, danh sách, bảng hoặc dialog có trách nhiệm rõ. Component nhận props/callback và có thể quản lý tương tác nội bộ như lựa chọn tab hoặc trạng thái mở.
- `hooks/`: tải dữ liệu, điều phối hành động, trạng thái bài làm và playback. Hook kết hợp state/effect/ref, quản lý request cũ, cleanup và khóa mutation.
- `api/`: hàm gọi HTTP và chuyển response về hợp đồng frontend; dùng `shared/api/client.ts`, không chứa JSX hoặc phụ thuộc màn hình.
- `model/`: hàm thuần xử lý dữ liệu, phép tính, quy tắc hiển thị và biến đổi state; có thể dùng ngoài React.
- `types.ts` hoặc `types/`: hợp đồng riêng của nghiệp vụ; dùng lại kiểu có sẵn khi cùng một dữ liệu.
- `layout/`, `styles/`: khung và style riêng của nhóm trang. CSS Modules đặt cạnh component/page khi phù hợp.

`shared/auth/types.ts` là định nghĩa User duy nhất. `shared/auth/sessionStore.ts` chỉ giữ user của phiên và thao tác cập nhật user; draft, bài tập, mô phỏng và playback thuộc feature đang sử dụng chúng. Store thư viện riêng thuộc `library`; các trạng thái cần tồn tại qua màn hình có chủ sở hữu nghiệp vụ rõ.

Tài khoản, trường học, gói, chương trình và vận hành có API ở feature tương ứng. `billing/api/billingApi.ts` quản lý hợp đồng thanh toán/gói; `management/api/operationsApi.ts` quản lý dữ liệu vận hành. Các màn hình quản trị dùng component theo nghiệp vụ thay cho một file chứa nhiều màn hình khác nhau.

`school/dataio` giữ nhập/xuất dữ liệu dạng file. Dữ liệu gửi AI thuộc API/model/hook mô phỏng; engine JavaScript giữ runtime, scene và renderer tách khỏi React UI. API và engine không import component/hook trình bày.

## Hướng phụ thuộc

Luồng chính: `app → feature pages → feature hooks/components → feature api/model → shared`. Page có thể ghép component và gọi API của nghiệp vụ liên quan qua hợp đồng rõ ràng.

- `shared` không import `app` hoặc `features`.
- Feature không import shell/entry của app; component, hook và model không import page.
- Khi dùng code của feature khác, lấy API, type, model hoặc component có thể dùng lại. Không lấy hook điều phối nội bộ hoặc trang làm component dùng chung.
- API/model/type/engine không phụ thuộc React, router, Radix hoặc Motion.
- `app/AppRoutes.tsx` giữ lazy route và access gate; bootstrap, mật khẩu lần đầu và realtime thuộc app. Backend kiểm tra quyền và phạm vi tài nguyên thực tế.

Không thêm file index chỉ để chuyển tiếp import hoặc wrapper chỉ đổi tên một component. Dùng một file khi các phần nhỏ cùng một nhiệm vụ; tách file khi có luồng trạng thái riêng, được dùng lại hoặc giúp giảm rõ độ phức tạp của màn hình.

## Composition và quản lý trạng thái

Dùng props, `children` và callback để ghép các thành phần hiện có. Danh sách trường nhập, action, tab hoặc navigation có cùng cấu trúc được mô tả bằng collection và render bằng `map`; điều kiện nghiệp vụ vẫn được viết rõ tại chủ sở hữu. Hàm thuần đặt trong model; chỉ dùng custom hook khi có state, effect, ref hoặc phối hợp hook khác.

`assignments/pages/StudentAssignments.tsx` ghép lớp học, tab, danh sách bài, workbench và cộng đồng. `useAssignmentPractice` quản lý dự đoán, nộp bài, điều chỉnh tham số, playback và nhật ký; `useStudentResources` quản lý lớp/cộng đồng và player tài nguyên. Workspace được key theo user để xóa state/draft khi đổi tài khoản. Request có kiểm tra phiên và số thứ tự; mutation có khóa bằng ref để chặn gửi lặp trước lần render tiếp theo.

Không đẩy mọi trạng thái vào Zustand. Dialog, filter, form và playback cục bộ thuộc component/hook đang dùng; dữ liệu dẫn xuất được tính từ nguồn hiện có. `useMemo`/`useCallback` dùng khi cần giữ identity hoặc tránh tính lại đáng kể, không bọc mọi giá trị/hàm.

## Thêm hoặc sửa chức năng

1. Chọn feature theo nghiệp vụ, đặt page trong `features/<feature>/pages`; component dùng cho nhiều màn hình thuộc `components`.
2. Gọi HTTP qua API của feature; thêm hook điều phối khi luồng có nhiều trạng thái hoặc request.
3. Dùng `shared/layout/PageContainer` cho trang dưới navbar. Workspace, management, school và reviewer có layout riêng.
4. Khai báo lazy route trong `app/AppRoutes.tsx`, dùng `RequireAccess` cho trang cần đăng nhập hoặc giới hạn role. Trang toàn màn hình khai báo trong `app/AppShell.tsx`.
5. Khi đổi tài nguyên, phiên hoặc unmount, abort request nếu API hỗ trợ signal; các request còn lại phải bỏ qua kết quả cũ. Hủy timer và tác vụ adjustment trong cleanup.
6. Dùng `*.module.css` cho style mới. Tránh selector toàn cục từ CSS của trang.
7. Chạy kiểm tra kiến trúc, lint, regression và build; kiểm tra trực tiếp desktop/mobile khi thay đổi UI.

## CSS và giao diện

`main.tsx` nạp `styles/global.css` một lần, theo thứ tự: Radix Themes → `tokens.css` → `base.css` → `modern-roles.css` → `admin-console.css`.

- `styles/tokens.css` là nguồn duy nhất cho màu, font, bo góc, đổ bóng (`--ink`, `--muted`, `--line`, `--surface*`, `--accent*`, `--success*`, `--warning*`, `--danger*`, `--night*`). Không thêm mã màu cứng trong CSS mới; dùng biến token.
- `styles/base.css` giữ reset, phần tử gốc (button, input, bảng) và vài class dùng chung (`.card`, `.status`, `.error`, `.brand-mark`, hiệu ứng).
- Thương hiệu dùng `shared/ui/BrandMark.tsx` (logo SVG theo token), không dùng `favicon.ico` làm logo.
- Hiệu ứng chuyển động dùng chung nằm trong `shared/effects`: `DotField` (lưới điểm phản ứng theo con trỏ, canvas 2D) và `Motion.tsx` (`SplitWords`, `Reveal`, `Magnetic`, `SpotlightCard`, `Marquee`). Chỉ dùng ở trang công khai, đăng nhập/đăng ký và phần chào học sinh; mọi hiệu ứng tôn trọng `prefers-reduced-motion` và dừng khi ra khỏi màn hình.
- Style riêng của trang đặt cạnh trang (CSS Module hoặc file trong `styles/` của feature). Khi xoá component, xoá luôn các selector CSS không còn dùng.

## Kiểm tra

- `npm run check:architecture`: kiểm tra vị trí source, import tĩnh/dynamic, hướng phụ thuộc, ranh giới shared/feature và việc nạp CSS nền.
- `npm run lint`: TypeScript/React hook lint.
- `npm test`: regression tests hiện có.
- `npm run build`: kiểm tra kiểu và production build.
- `npm run check`: chạy toàn bộ các bước trên.

Kiểm tra tự động không thay thế việc kiểm tra giao diện trong trình duyệt.
