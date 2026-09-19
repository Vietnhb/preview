# Supabase setup

## Đăng ký trường và VNPAY Sandbox

Trang `/signup` lấy danh mục gói từ `license_plans`. Giá khởi tạo theo
`B2B_SYSTEM_DESIGN.md`; seed không ghi đè những thay đổi sau đó của admin.
`school_payments` lưu số tiền và quota tại thời điểm tạo giao dịch.
Chạy phần tạo hai bảng này trong `src/main/resources/data.sql` trên Supabase
trước khi khởi động nếu dùng `JPA_DDL_AUTO=validate`.

Điền trong `backend/.env.local` bằng thông tin merchant Sandbox của bạn:

```properties
VNPAY_TMN_CODE=<merchant-code>
VNPAY_HASH_SECRET=<sandbox-secret>
VNPAY_RETURN_URL=http://localhost:5173/signup/payment-result
```

Đăng ký IPN URL với VNPAY:
`https://<public-backend>/api/auth/payments/vnpay/ipn`.
VNPAY cần truy cập được URL này qua Internet; `localhost` không nhận được IPN.
Return URL dùng để hiển thị kết quả, không kích hoạt gói.
Backend kiểm tra HMAC-SHA512, merchant, số tiền và trạng thái giao dịch;
khóa dòng thanh toán để xử lý callback trùng. Chỉ IPN hợp lệ với cả response code
và transaction status `00` mới mở trường/tài khoản quản lý và cấp license một năm.
Admin xem đăng ký đã thanh toán trong menu chuông thông báo.

Thanh toán thất bại giữ tài khoản/trường ở trạng thái chưa kích hoạt.
Hiện cần admin hỗ trợ thanh toán lại; chưa có luồng retry hoặc hoàn tiền tự động.
Chưa kiểm thử giao dịch thực với merchant Sandbox.

[Tài liệu tích hợp VNPAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

Backend kết nối Supabase bằng PostgreSQL/JPA. Dự án không dùng Prisma; các lệnh
`npm install prisma` và `prisma init` chỉ dành cho backend Node.js.

## 1. Khai báo biến môi trường

PowerShell:

```powershell
$env:SUPABASE_DB_URL="jdbc:postgresql://<host>:5432/postgres?sslmode=require"
$env:SUPABASE_DB_USERNAME="postgres"
$env:SUPABASE_DB_PASSWORD="<database-password>"
$env:JWT_SECRET="<local-jwt-secret>"
```

Nếu dùng Supabase pooler port `6543`, thêm `&prepareThreshold=0` vào URL.

Hoặc tạo `backend/.env.local` theo connection string mà Supabase cung cấp ở
**Connect → ORM**:

```properties
DATABASE_URL=postgresql://<username>:<password>@<pooler-host>:6543/postgres?pgbouncer=true
DIRECT_URL=postgresql://<username>:<password>@<pooler-host>:5432/postgres
JWT_SECRET=<at-least-32-random-characters>
```

Backend tự chuyển `DATABASE_URL` sang JDBC, bỏ tham số Prisma `pgbouncer=true`
và cấu hình `prepareThreshold=0` cho transaction pooler. `DIRECT_URL` là fallback.
Thay toàn bộ placeholder `<password>` hoặc `[YOUR-PASSWORD]`; dấu ngoặc vuông
không phải một phần của password.

Không dùng `anon key` hoặc `service_role key` làm mật khẩu PostgreSQL.

## 2. Khởi tạo schema B2B trên Supabase

`data.sql` hiện là migration tương thích PostgreSQL/Supabase: tạo role mới,
đổi tên reviewer cũ, tạo bảng lớp/enrollment/phân công giáo viên, trigger kiểm
tra role-school, index manager duy nhất và index enrollment active. Hãy chạy nội dung file này một lần trong Supabase
SQL Editor hoặc qua kết nối session/direct port `5432`, không chạy qua transaction
pooler `6543`.

Sau khi migration thành công, đặt:

```properties
JPA_DDL_AUTO=validate
SQL_INIT_MODE=never
```

Nếu muốn Spring chạy seed/migration này trong môi trường tạm thời, dùng
`JPA_DDL_AUTO=update` và `SQL_INIT_MODE=always`. Không dùng cấu hình đó cho
production vì `data.sql` sẽ chạy lại ở mỗi lần khởi động.

## 3. Chạy backend

```powershell
.\mvnw.cmd spring-boot:run
```

Các seed runner của ứng dụng vẫn tạo dữ liệu curriculum/schema/demo cần thiết;
schema B2B nên được migration trước trên Supabase.

## 4. Cấu hình production

Sau khi schema ổn định, đặt:

```powershell
$env:JPA_DDL_AUTO="validate"
```

Thông tin thật chỉ đặt trong environment/local config; không commit `application.properties`.
