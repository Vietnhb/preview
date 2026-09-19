# Kiến trúc frontend PhysLive

## Trách nhiệm từng tầng

- `src/app/`: khởi tạo phiên đăng nhập, routing, phân quyền, shell, license.
- `src/pages/`: màn hình theo URL; ghép các thành phần và hook tính năng.
- `src/features/<feature>/components/`: giao diện theo nghiệp vụ, dùng được cho nhiều vai trò.
- `src/features/<feature>/hooks/`: tải dữ liệu, trạng thái tương tác và điều phối nghiệp vụ.
- `src/shared/`: thành phần không phụ thuộc nghiệp vụ. `shared/layout/PageContainer` là khung trang tiêu chuẩn.
- `src/api/`: các hàm gọi HTTP qua axios client hiện có; không thêm HTTP client riêng trong component.
- `src/types/`, `src/utils/`: hợp đồng dữ liệu và hàm xử lý nền tảng.
- `src/store/`: trạng thái cần chia sẻ giữa các màn hình. Trạng thái dialog, bộ lọc, playback cục bộ đặt trong hook tính năng.
- Các thư mục simulation hiện có tiếp tục giữ riêng vì chứa engine và renderer chuyên biệt.

Luồng phụ thuộc: app → pages → features → shared. Feature có thể dùng api, types, utils và các component simulation dùng chung hiện có. Shared không import app/pages/features. API/types/utils không import app/pages/features.

## Tạo trang mới

1. Tạo page mỏng trong `pages/<domain>`, đưa logic tương tác vào hook tính năng nếu có nhiều trạng thái.
2. Dùng `PageContainer` cho trang dưới navbar. Workspace, admin, school và reviewer giữ layout chuyên biệt.
3. Dùng `*.module.css` cho CSS mới. Không sửa `body`, `:root`, `.main` hoặc `button` toàn cục từ CSS của trang.
4. Khai báo route lazy trong `app/AppRoutes.tsx`; dùng `RequireAccess` cho trang cần đăng nhập hoặc giới hạn vai trò. Backend vẫn chịu trách nhiệm kiểm tra quyền thực tế.
5. Nếu trang cần toàn màn hình, khai báo tại `app/AppShell.tsx`.
6. Request bất đồng bộ phải bỏ qua kết quả cũ khi đổi lựa chọn hoặc unmount. Xem `useCommunityLibrary`.
7. Chạy `npm run check`; kiểm tra trực tiếp desktop/mobile và chuyển trang qua lại để phát hiện CSS rò rỉ.

## CSS hiện tại và lộ trình chuyển đổi

`main.tsx` nạp `styles/global.css` một lần. File này cố định thứ tự bốn stylesheet nền cũ; page không import lại chúng. Việc nạp sớm CSS cũ tăng CSS khởi động nhưng loại bỏ thay đổi giao diện theo lịch sử điều hướng.

Các stylesheet và component cũ chưa được chuyển hết. Khi sửa một tính năng, chuyển dần style riêng sang CSS Modules và logic sang features; không tạo thêm lớp override toàn cục. `ResourceDiscovery` đã chuyển về feature library, nhưng vẫn dùng các class CSS tương thích hiện có.

## Kiểm tra

- `npm run check:architecture`: kiểm tra import tĩnh và dynamic import, ranh giới shared/features và việc nạp CSS nền.
- `npm run lint`: TypeScript/React hook lint.
- `npm test`: regression tests hiện có.
- `npm run build`: kiểm tra kiểu và build production.
- `npm run check`: chạy toàn bộ các bước trên.

Kiểm tra tự động không thay thế kiểm tra giao diện trong trình duyệt. Quy tắc hiện tại áp dụng cho ranh giới mới, không tuyên bố toàn bộ legacy đã được chuyển đổi.
