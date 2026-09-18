# Supabase setup

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
đổi tên reviewer cũ, tạo trigger kiểm tra role-school, index manager duy nhất
và index enrollment active. Hãy chạy nội dung file này một lần trong Supabase
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
