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

## 2. Chạy backend

```powershell
.\mvnw.cmd spring-boot:run
```

Lần chạy đầu sẽ tạo/cập nhật bảng `roles`, `users` và thêm role `GUEST`.

## 3. Cấu hình production

Sau khi schema ổn định, đặt:

```powershell
$env:JPA_DDL_AUTO="validate"
```

Thông tin thật chỉ đặt trong environment/local config; không commit `application.properties`.
