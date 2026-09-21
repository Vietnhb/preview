// Data cho 38 bảng database PhysLive - Tiếng Việt với Ví dụ và Luồng

const roleLabels = {
    'admin': 'Quản trị viên',
    'reviewer': 'Chuyên gia',
    'manager': 'Quản lý trường',
    'teacher': 'Giáo viên',
    'student': 'Học sinh',
    'system': 'Hệ thống'
};

const tables = [
    // GROUP 1: Xác thực & Phân quyền
    {
        name: "users",
        dbName: "users",
        category: "auth",
        categoryName: "Xác thực & Phân quyền",
        purpose: "Tài khoản người dùng cho tất cả 5 vai trò (Admin, Chuyên gia, Quản lý trường, Giáo viên, Học sinh)",
        roles: ["admin", "reviewer", "manager", "teacher", "student"],
        columns: [
            { name: "id", type: "SERIAL PRIMARY KEY", description: "ID tự động tăng" },
            { name: "email", type: "VARCHAR(255) UNIQUE", description: "Email đăng nhập" },
            { name: "password", type: "VARCHAR(255)", description: "Mật khẩu (Bcrypt hash)" },
            { name: "full_name", type: "VARCHAR(255)", description: "Họ và tên" },
            { name: "role_id", type: "INTEGER FK", description: "Vai trò (1-5)" },
            { name: "school_id", type: "UUID FK", description: "NULL cho vai trò platform, NOT NULL cho vai trò trường" },
            { name: "active", type: "BOOLEAN", description: "Cờ xóa mềm (soft delete)" }
        ],
        example: `VÍ DỤ DỮ LIỆU:

| id  | email                | full_name          | role_id | school_id | active |
|-----|----------------------|--------------------|---------|-----------|--------|
| 1   | admin@physlive.vn    | Admin PhysLive     | 1 (ADMIN)| NULL     | true   |
| 2   | reviewer@physlive.vn | Thầy Hùng          | 2 (REVIEWER)| NULL  | true   |
| 101 | hieu.ql@lhp.edu.vn   | Ông Nguyễn Văn Hiếu| 3 (MANAGER)| uuid-lhp| true   |
| 102 | minh.gv@lhp.edu.vn   | Thầy Nguyễn Văn Minh| 4 (TEACHER)| uuid-lhp| true   |
| 201 | hung.hs@lhp.edu.vn   | Em Trần Văn Hùng   | 5 (STUDENT)| uuid-lhp| true   |`,
        flow: [
            { step: 1, desc: "User mở trình duyệt, nhập email/password" },
            { step: 2, desc: "Hệ thống query bảng 'users' WHERE email = ?" },
            { step: 3, desc: "Kiểm tra password hash, active = true" },
            { step: 4, desc: "JOIN 'schools' kiểm tra license còn hạn" },
            { step: 5, desc: "Tạo JWT token → INSERT vào 'class_sessions'" },
            { step: 6, desc: "User đăng nhập thành công" }
        ],
        businessRules: [
            "Vai trò Platform (ADMIN, REVIEWER): school_id PHẢI NULL",
            "Vai trò Trường (MANAGER, TEACHER, STUDENT): school_id KHÔNG NULL",
            "Chỉ soft delete (active = false), KHÔNG xóa cứng",
            "Mỗi trường CHỈ có 1 SCHOOL_MANAGER active"
        ]
    },
    
    {
        name: "schools",
        dbName: "schools",
        category: "auth",
        categoryName: "Xác thực & Phân quyền",
        purpose: "Khách hàng B2B (trường học) với license và AI quota management",
        roles: ["admin", "manager", "teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "School ID" },
            { name: "name", type: "VARCHAR(255) UNIQUE", description: "Tên trường" },
            { name: "license_start", type: "DATE", description: "Ngày bắt đầu license" },
            { name: "license_end", type: "DATE", description: "Ngày hết hạn license" },
            { name: "monthly_token_quota", type: "INTEGER", description: "Quota AI tokens/tháng" },
            { name: "used_tokens", type: "BIGINT", description: "Tokens đã dùng trong tháng" }
        ],
        example: `VÍ DỤ DỮ LIỆU:

| id       | name                    | license_start | license_end | quota | used  | active |
|----------|-------------------------|---------------|-------------|-------|-------|--------|
| uuid-lhp | THPT Lê Hồng Phong      | 2026-01-01    | 2026-12-31  | 5000  | 1234  | true   |
| uuid-lqd | THPT Lê Quý Đôn         | 2026-03-01    | 2027-02-28  | 3000  | 567   | true   |
| uuid-nvt | THPT Nguyễn Văn Trỗi    | 2025-09-01    | 2026-08-31  | 8000  | 2345  | true   |`,
        flow: [
            { step: 1, desc: "Teacher tạo simulation mới (click 'Tạo mô phỏng')" },
            { step: 2, desc: "Hệ thống SELECT * FROM schools WHERE id = teacher.school_id" },
            { step: 3, desc: "Kiểm tra: license_end >= CURRENT_DATE (còn hạn?)" },
            { step: 4, desc: "Kiểm tra: used_tokens < monthly_token_quota (đủ quota?)" },
            { step: 5, desc: "Nếu OK: Gọi AI API, UPDATE schools SET used_tokens = used_tokens + 150" },
            { step: 6, desc: "INSERT vào 'simulations' + 'token_usage_audits'" }
        ],
        businessRules: [
            "License hết hạn → Read-only mode (login được nhưng không tạo mới)",
            "Quota vượt mức → Block tạo simulation",
            "Reset quota tự động vào ngày 1 hàng tháng",
            "Show banner cảnh báo khi còn < 30 ngày hoặc quota > 80%"
        ]
    },
    
    {
        name: "simulations",
        dbName: "simulations",
        category: "teaching",
        categoryName: "Giảng dạy & Học tập",
        purpose: "Metadata của mô phỏng (được tạo bởi Teacher/Admin) với visibility và review status",
        roles: ["admin", "reviewer", "teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Simulation ID" },
            { name: "title", type: "VARCHAR(255)", description: "Tiêu đề mô phỏng" },
            { name: "visibility", type: "VARCHAR(20)", description: "PERSONAL, SHARED, PUBLIC" },
            { name: "review_status", type: "VARCHAR(20)", description: "NOT_SUBMITTED, PENDING, APPROVED, REJECTED" },
            { name: "created_by", type: "INTEGER FK", description: "Teacher hoặc Admin tạo" },
            { name: "school_id", type: "UUID FK", description: "NULL nếu Admin tạo" }
        ],
        example: `VÍ DỤ DỮ LIỆU:

| id        | title                           | visibility | review_status | created_by | school_id |
|-----------|---------------------------------|------------|---------------|------------|-----------|
| uuid-sim1 | Chuyển động ném ngang - Bài 1  | PERSONAL   | NOT_SUBMITTED | 102        | uuid-lhp  |
| uuid-sim2 | Dao động điều hòa              | SHARED     | APPROVED      | 102        | uuid-lhp  |
| uuid-sim3 | Định luật Newton                | SHARED     | PENDING       | 105        | uuid-lqd  |`,
        flow: [
            { step: 1, desc: "Teacher tạo simulation → visibility = 'PERSONAL'" },
            { step: 2, desc: "Teacher click 'Share to Community' → visibility = 'SHARED', review_status = 'PENDING'" },
            { step: 3, desc: "REVIEWER login → SELECT * FROM simulations WHERE review_status = 'PENDING'" },
            { step: 4, desc: "REVIEWER test simulation, kiểm tra physics" },
            { step: 5, desc: "REVIEWER approve → review_status = 'APPROVED', INSERT vào 'library_moderation_audits'" },
            { step: 6, desc: "Simulation hiển thị trong Shared Library cho tất cả Teachers" }
        ],
        businessRules: [
            "PERSONAL: Chỉ creator thấy",
            "SHARED: Sau khi REVIEWER approve → tất cả teachers thấy",
            "PUBLIC: Tính năng tương lai (cho học sinh tự do chạy)",
            "Mỗi lần chạy simulation → INSERT vào 'simulation_runs'"
        ]
    },
    
    {
        name: "assignments",
        dbName: "assignments",
        category: "teaching",
        categoryName: "Giảng dạy & Học tập",
        purpose: "Bài tập được giao bởi Teacher cho Class",
        roles: ["teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Assignment ID" },
            { name: "title", type: "VARCHAR(255)", description: "Tiêu đề bài tập" },
            { name: "simulation_id", type: "UUID FK", description: "Simulation sử dụng" },
            { name: "class_id", type: "UUID FK", description: "Lớp được giao" },
            { name: "due_date", type: "TIMESTAMP", description: "Deadline nộp bài" },
            { name: "max_attempts", type: "INTEGER", description: "Số lần nộp tối đa" }
        ],
        example: `VÍ DỤ WORKFLOW - Teacher giao bài:

Bước 1: Thầy Minh (teacher_id=102) tạo assignment
INSERT INTO assignments (id, title, simulation_id, class_id, teacher_id, due_date, max_attempts, status)
VALUES (
    uuid-assign-001,
    'Bài tập: Chuyển động ném ngang',
    uuid-sim1,
    uuid-class-10a1,  -- Lớp 10A1
    102,
    '2026-09-20 23:59:59',
    3,
    'ACTIVE'
);

Bước 2: Tự động tạo submissions cho tất cả học sinh trong lớp
INSERT INTO assignment_submissions (id, assignment_id, student_id, status, grading_status)
SELECT 
    gen_random_uuid(),
    'uuid-assign-001',
    student_id,
    'NOT_STARTED',
    'PENDING'
FROM class_enrollments
WHERE class_id = 'uuid-class-10a1' AND status = 'ACTIVE';

→ Tạo 42 rows (42 học sinh lớp 10A1)`,
        flow: [
            { step: 1, desc: "Teacher chọn simulation, chọn class, set deadline" },
            { step: 2, desc: "INSERT vào 'assignments'" },
            { step: 3, desc: "SELECT student_id FROM class_enrollments WHERE class_id = ?" },
            { step: 4, desc: "Loop: INSERT vào 'assignment_submissions' cho từng student" },
            { step: 5, desc: "Send notification (email/push) cho học sinh" },
            { step: 6, desc: "Students login → Thấy assignment mới trong dashboard" }
        ],
        businessRules: [
            "Mỗi student tự động có 1 submission record (status='NOT_STARTED')",
            "Max attempts enforced (VD: 3 lần nộp)",
            "Sau deadline: Student vẫn nộp được nhưng marked 'LATE'",
            "Teacher có thể extend deadline"
        ]
    },
    
    {
        name: "assignment_submissions",
        dbName: "assignment_submissions",
        category: "teaching",
        categoryName: "Giảng dạy & Học tập",
        purpose: "Bài nộp của học sinh cho assignments",
        roles: ["teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Submission ID" },
            { name: "assignment_id", type: "UUID FK", description: "Assignment reference" },
            { name: "student_id", type: "INTEGER FK", description: "Student reference" },
            { name: "status", type: "VARCHAR(20)", description: "NOT_STARTED, IN_PROGRESS, SUBMITTED" },
            { name: "score", type: "DECIMAL(5,2)", description: "Điểm số (0-20)" },
            { name: "grading_status", type: "VARCHAR(20)", description: "PENDING, GRADED" }
        ],
        example: `VÍ DỤ WORKFLOW - Student làm bài:

Bước 1: Em Hùng (student_id=201) mở assignment
SELECT * FROM assignment_submissions 
WHERE assignment_id = 'uuid-assign-001' AND student_id = 201;

→ Result: status='NOT_STARTED', attempt_count=0

Bước 2: Em Hùng chạy simulation
INSERT INTO simulation_runs (id, simulation_id, user_id, assignment_id, input_parameters, output_results)
VALUES (uuid-run-001, uuid-sim1, 201, 'uuid-assign-001', '{"v0":10, "h0":20}', '{"t":2.02, "range":20.2}');

Bước 3: Em Hùng nộp bài
UPDATE assignment_submissions
SET 
    status = 'SUBMITTED',
    submitted_at = NOW(),
    attempt_count = attempt_count + 1,
    answers = '{"q1":"2.02 giây", "q2":"20.2 mét"}',
    simulation_run_id = uuid-run-001
WHERE id = (SELECT id FROM assignment_submissions WHERE assignment_id = 'uuid-assign-001' AND student_id = 201);

Bước 4: AI auto-grade
UPDATE assignment_submissions
SET 
    grading_status = 'GRADED',
    score = 18,  -- 18/20 điểm
    ai_feedback = 'Câu 1 đúng (10đ). Câu 2 sai đơn vị (8đ).'
WHERE id = ...;

Bước 5: Teacher review & confirm
-- Teacher có thể điều chỉnh điểm
UPDATE assignment_submissions
SET score = 19, teacher_feedback = 'Em làm tốt! Lần sau chú ý đơn vị.'
WHERE id = ...;`,
        flow: [
            { step: 1, desc: "Student opens assignment → status='IN_PROGRESS'" },
            { step: 2, desc: "Student runs simulation → INSERT 'simulation_runs'" },
            { step: 3, desc: "Student submits answers → UPDATE status='SUBMITTED'" },
            { step: 4, desc: "AI auto-grades → UPDATE grading_status='GRADED', score=X" },
            { step: 5, desc: "Teacher reviews → Điều chỉnh điểm nếu cần" },
            { step: 6, desc: "Student sees final grade" }
        ],
        businessRules: [
            "AI chấm tự động → Teacher confirm/adjust",
            "Max attempts enforced (VD: 3 lần)",
            "Late submission allowed nhưng marked 'LATE'",
            "Teacher có thể re-grade"
        ]
    },
    
    {
        name: "library_folders",
        dbName: "library_folders",
        category: "library",
        categoryName: "Thư viện",
        purpose: "Thư mục cá nhân của Teacher để tổ chức simulations (giống Google Drive)",
        roles: ["teacher"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Folder ID" },
            { name: "owner_id", type: "INTEGER FK", description: "Teacher sở hữu" },
            { name: "name", type: "VARCHAR(120)", description: "Tên thư mục" },
            { name: "active", type: "BOOLEAN", description: "Soft delete" }
        ],
        example: `VÍ DỤ - Thầy Minh tổ chức thư viện:

📁 Chuyển động học (Khối 10)
   ├─ Chuyển động ném ngang - Bài 1
   ├─ Chuyển động ném ngang - Bài 2
   └─ Chuyển động tròn đều

📁 Động lực học (Khối 11)
   ├─ Định luật Newton I
   ├─ Định luật Newton II
   └─ Định luật Newton III

📁 Sóng cơ (Khối 12)
   └─ Giao thoa sóng

SQL:
INSERT INTO library_folders (id, owner_id, name, name_key)
VALUES 
  (uuid-f1, 102, 'Chuyển động học', 'chuyen-dong-hoc'),
  (uuid-f2, 102, 'Động lực học', 'dong-luc-hoc'),
  (uuid-f3, 102, 'Sóng cơ', 'song-co');`,
        flow: [
            { step: 1, desc: "Teacher click 'Tạo thư mục mới'" },
            { step: 2, desc: "INSERT INTO library_folders (name, name_key)" },
            { step: 3, desc: "Teacher drag & drop simulation vào folder" },
            { step: 4, desc: "UPDATE library_items SET folder_id = uuid-f1" },
            { step: 5, desc: "Teacher xem: SELECT * FROM library_items WHERE folder_id = uuid-f1" }
        ],
        businessRules: [
            "Mỗi teacher có folders riêng (không thấy folder của người khác)",
            "Không được trùng tên (case-insensitive)",
            "Chỉ xóa được folder rỗng (phải move items ra trước)",
            "Soft delete (active=false)"
        ]
    },
    
    {
        name: "library_items",
        dbName: "library_items",
        category: "library",
        categoryName: "Thư viện",
        purpose: "Link simulations vào folders + quản lý Shared Library",
        roles: ["reviewer", "teacher"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Item ID" },
            { name: "folder_id", type: "UUID FK", description: "Thuộc folder nào" },
            { name: "simulation_id", type: "UUID FK", description: "Simulation reference" },
            { name: "visibility", type: "VARCHAR(16)", description: "PERSONAL, SHARED" },
            { name: "moderation_status", type: "VARCHAR(16)", description: "APPROVED, PENDING, REJECTED, FEATURED" }
        ],
        example: `VÍ DỤ WORKFLOW - Share to Community:

Bước 1: Teacher có simulation tốt, muốn share
UPDATE library_items
SET visibility = 'SHARED', moderation_status = 'PENDING'
WHERE id = 'uuid-item-123';

Bước 2: REVIEWER nhận notification, xem queue
SELECT 
    li.id, li.title, u.full_name AS submitted_by, s.name AS school
FROM library_items li
JOIN users u ON li.owner_id = u.id
JOIN schools s ON u.school_id = s.id
WHERE li.moderation_status = 'PENDING'
ORDER BY li.created_at ASC;

Bước 3: REVIEWER test simulation, kiểm tra physics

Bước 4a: REVIEWER APPROVE
UPDATE library_items
SET moderation_status = 'APPROVED', 
    moderated_by = 2,  -- REVIEWER ID
    moderated_at = NOW(),
    moderation_comment = 'Simulation chất lượng tốt, physics chính xác'
WHERE id = 'uuid-item-123';

INSERT INTO library_moderation_audits (library_item_id, reviewer_id, from_status, to_status, comment)
VALUES ('uuid-item-123', 2, 'PENDING', 'APPROVED', 'Simulation chất lượng tốt...');

→ Giờ tất cả teachers có thể thấy và copy simulation này

Bước 4b: REVIEWER REJECT (nếu có lỗi)
UPDATE library_items
SET moderation_status = 'REJECTED',
    moderation_comment = 'Lỗi công thức: gravity nên là 9.8 m/s², không phải 10'
WHERE id = 'uuid-item-123';

→ Teacher nhận feedback, sửa lại, submit lại`,
        flow: [
            { step: 1, desc: "Teacher click 'Share to Community'" },
            { step: 2, desc: "UPDATE visibility='SHARED', moderation_status='PENDING'" },
            { step: 3, desc: "REVIEWER reviews: Test + Check physics" },
            { step: 4, desc: "REVIEWER approves/rejects" },
            { step: 5, desc: "INSERT vào 'library_moderation_audits'" },
            { step: 6, desc: "If approved: All teachers can copy" }
        ],
        businessRules: [
            "PERSONAL: Chỉ owner thấy (auto-approved)",
            "SHARED: Cần REVIEWER approve trước khi public",
            "FEATURED: High quality, được promote trong UI",
            "REMOVED: Vi phạm quy định"
        ]
    },
    
    {
        name: "school_classes",
        dbName: "school_classes",
        category: "school",
        categoryName: "Quản lý Trường học",
        purpose: "Các lớp học trong trường (10A1, 11B2, v.v.)",
        roles: ["manager", "teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Class ID" },
            { name: "school_id", type: "UUID FK", description: "Trường học" },
            { name: "name", type: "VARCHAR(100)", description: "Tên lớp (10A1)" },
            { name: "grade", type: "INTEGER", description: "Khối (10, 11, 12)" },
            { name: "school_year", type: "VARCHAR(20)", description: "Năm học" }
        ],
        example: `VÍ DỤ - SCHOOL_MANAGER tạo lớp:

INSERT INTO school_classes (id, school_id, name, grade, school_year, created_by)
VALUES 
  (uuid-10a1, 'uuid-lhp', '10A1', 10, '2026-2027', 101),
  (uuid-10a2, 'uuid-lhp', '10A2', 10, '2026-2027', 101),
  (uuid-11b1, 'uuid-lhp', '11B1', 11, '2026-2027', 101);

Sau đó enroll students:
-- Lớp 10A1 có 42 học sinh
-- Lớp 10A2 có 38 học sinh
-- Lớp 11B1 có 40 học sinh`,
        flow: [
            { step: 1, desc: "SCHOOL_MANAGER tạo lớp mới" },
            { step: 2, desc: "INSERT INTO school_classes" },
            { step: 3, desc: "SCHOOL_MANAGER enroll students (import CSV hoặc thủ công)" },
            { step: 4, desc: "INSERT INTO class_enrollments cho từng student" },
            { step: 5, desc: "SCHOOL_MANAGER assign teachers" },
            { step: 6, desc: "INSERT INTO class_teacher_assignments" }
        ],
        businessRules: [
            "Tạo bởi SCHOOL_MANAGER",
            "Max 45 students per class (có thể config)",
            "1 class thuộc 1 school",
            "Lớp có danh sách students sẵn"
        ]
    },
    
    {
        name: "class_enrollments",
        dbName: "class_enrollments",
        category: "school",
        categoryName: "Quản lý Trường học",
        purpose: "Map học sinh vào lớp (1 học sinh = 1 lớp/năm học)",
        roles: ["manager", "teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Enrollment ID" },
            { name: "class_id", type: "UUID FK", description: "Lớp học" },
            { name: "student_id", type: "INTEGER FK", description: "Học sinh" },
            { name: "status", type: "VARCHAR(20)", description: "ACTIVE, TRANSFERRED, GRADUATED" }
        ],
        example: `VÍ DỤ - Constraint: 1 học sinh chỉ ở 1 lớp/năm

-- ✅ VALID: Em Hùng ở lớp 10A1 năm 2026-2027
INSERT INTO class_enrollments (class_id, student_id, school_year, status)
VALUES ('uuid-10a1', 201, '2026-2027', 'ACTIVE');

-- ❌ INVALID: Em Hùng không thể ở 2 lớp cùng lúc
INSERT INTO class_enrollments (class_id, student_id, school_year, status)
VALUES ('uuid-10a2', 201, '2026-2027', 'ACTIVE');
→ Error: UNIQUE constraint violation

-- ✅ TRANSFER: Chuyển lớp
UPDATE class_enrollments 
SET status = 'TRANSFERRED'
WHERE student_id = 201 AND class_id = 'uuid-10a1';

INSERT INTO class_enrollments (class_id, student_id, school_year, status)
VALUES ('uuid-10a2', 201, '2026-2027', 'ACTIVE');`,
        flow: [
            { step: 1, desc: "SCHOOL_MANAGER enroll student vào class" },
            { step: 2, desc: "Check: Student đã có enrollment ACTIVE chưa?" },
            { step: 3, desc: "If yes: Block (1 student = 1 class)" },
            { step: 4, desc: "If no: INSERT INTO class_enrollments" },
            { step: 5, desc: "Student login → Thấy class của mình" }
        ],
        businessRules: [
            "UNIQUE constraint: (student_id, school_year) WHERE status='ACTIVE'",
            "1 student chỉ ở 1 lớp/năm học",
            "Transfer: Set old → TRANSFERRED, create new → ACTIVE",
            "Graduation: Set status → GRADUATED"
        ]
    },

    
    {
        name: "roles",
        dbName: "roles",
        category: "auth",
        categoryName: "Xác thực & Phân quyền",
        purpose: "5 vai trò trong hệ thống (ADMIN, REVIEWER, SCHOOL_MANAGER, TEACHER, STUDENT)",
        roles: ["admin"],
        columns: [
            { name: "id", type: "SERIAL PRIMARY KEY", description: "1-5" },
            { name: "name", type: "VARCHAR(50) UNIQUE", description: "Tên vai trò" }
        ],
        example: `VÍ DỤ DỮ LIỆU:

| id | name               | description                           |
|----|--------------------|---------------------------------------|
| 1  | ADMIN              | Platform admin - Quản trị toàn hệ thống |
| 2  | REVIEWER   | Chuyên gia kiểm duyệt nội dung physics |
| 3  | SCHOOL_MANAGER     | Quản lý trường - 1 người/trường        |
| 4  | TEACHER            | Giáo viên - Nhiều người/trường         |
| 5  | STUDENT            | Học sinh                               |`,
        flow: [
            { step: 1, desc: "Hệ thống khởi tạo: INSERT 5 roles cố định" },
            { step: 2, desc: "User signup → chọn role_id (3, 4, hoặc 5)" },
            { step: 3, desc: "Admin tạo user → chọn bất kỳ role_id nào (1-5)" },
            { step: 4, desc: "JWT token chứa role_id để authorization" }
        ],
        businessRules: [
            "5 roles HARD-CODED, không thêm/xóa/sửa",
            "Platform roles (1-2): school_id = NULL",
            "School roles (3-5): school_id NOT NULL",
            "1 trường CHỈ có 1 SCHOOL_MANAGER active"
        ]
    },

    {
        name: "class_sessions",
        dbName: "class_sessions",
        category: "auth",
        categoryName: "Xác thực & Phân quyền",
        purpose: "JWT sessions - track active logins cho revoke token",
        roles: ["admin", "reviewer", "manager", "teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Session ID" },
            { name: "user_id", type: "INTEGER FK", description: "User đăng nhập" },
            { name: "token_hash", type: "VARCHAR(255)", description: "Hash của JWT" },
            { name: "expires_at", type: "TIMESTAMP", description: "Thời gian hết hạn" },
            { name: "revoked", type: "BOOLEAN", description: "Đã thu hồi chưa" }
        ],
        example: `VÍ DỤ - Force logout (revoke token):

Bước 1: Admin phát hiện user vi phạm
Bước 2: Admin click "Force Logout"
UPDATE class_sessions SET revoked = true WHERE user_id = 102;

Bước 3: User request API
→ JwtFilter check: SELECT revoked FROM class_sessions WHERE token_hash = ?
→ If revoked = true → Return 401 Unauthorized

Bước 4: User phải login lại`,
        flow: [
            { step: 1, desc: "User login thành công → JWT token được tạo" },
            { step: 2, desc: "INSERT vào class_sessions (token_hash, user_id, expires_at)" },
            { step: 3, desc: "Mỗi API request → JwtFilter check revoked = false" },
            { step: 4, desc: "Admin revoke → UPDATE revoked = true" },
            { step: 5, desc: "Cronjob định kỳ: DELETE sessions WHERE expires_at < NOW()" }
        ],
        businessRules: [
            "1 user có thể có nhiều sessions (nhiều thiết bị)",
            "Revoke = soft logout (không cần database password change)",
            "TTL mặc định: 7 ngày (configurable)",
            "Cleanup job chạy hàng ngày xóa expired sessions"
        ]
    },

    {
        name: "license_plans",
        dbName: "license_plans",
        category: "school",
        categoryName: "Quản lý Trường học",
        purpose: "Các gói license B2B (Basic, Pro, Enterprise) cho trường mua",
        roles: ["admin", "manager"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Plan ID" },
            { name: "name", type: "VARCHAR(100)", description: "Tên gói" },
            { name: "price_vnd", type: "DECIMAL(12,2)", description: "Giá VNĐ/năm" },
            { name: "monthly_token_quota", type: "INTEGER", description: "AI tokens/tháng" },
            { name: "max_teachers", type: "INTEGER", description: "Số GV tối đa" },
            { name: "max_students", type: "INTEGER", description: "Số HS tối đa" }
        ],
        example: `VÍ DỤ 3 GÓI LICENSE:

| name       | price_vnd   | token_quota | max_teachers | max_students |
|------------|-------------|-------------|--------------|--------------|
| Basic      | 20,000,000  | 3,000       | 10           | 500          |
| Pro        | 50,000,000  | 8,000       | 30           | 1,500        |
| Enterprise | 150,000,000 | 25,000      | 100          | 5,000        |

WORKFLOW MUA LICENSE:
1. SCHOOL_MANAGER chọn plan (VD: Pro)
2. Click "Thanh toán VNPay"
3. Redirect VNPay → Quét QR → Thanh toán 50tr
4. VNPay callback → UPDATE schools SET license_plan_id = 'pro', license_end = NOW() + 1 year
5. INSERT vào school_payments (amount, status = 'PAID')`,
        flow: [
            { step: 1, desc: "Admin tạo plans (1 lần duy nhất)" },
            { step: 2, desc: "School signup → Chọn plan" },
            { step: 3, desc: "VNPay payment → Success" },
            { step: 4, desc: "UPDATE schools.license_plan_id, license_end" },
            { step: 5, desc: "School hoạt động theo quota của plan" }
        ],
        businessRules: [
            "Plans cố định (không cho school tự custom)",
            "Price tính theo năm (yearly subscription)",
            "Upgrade/downgrade: Tính theo prorated",
            "Renewal trước 30 ngày → Discount 10%"
        ]
    },

    {
        name: "school_payments",
        dbName: "school_payments",
        category: "school",
        categoryName: "Quản lý Trường học",
        purpose: "Lịch sử thanh toán license của trường (VNPay integration)",
        roles: ["admin", "manager"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Payment ID" },
            { name: "school_id", type: "UUID FK", description: "Trường thanh toán" },
            { name: "plan_id", type: "UUID FK", description: "Gói đã mua" },
            { name: "amount_vnd", type: "DECIMAL(12,2)", description: "Số tiền" },
            { name: "vnpay_transaction_id", type: "VARCHAR(100)", description: "VNPay txn_id" },
            { name: "status", type: "VARCHAR(20)", description: "PENDING, PAID, FAILED, REFUNDED" }
        ],
        example: `VÍ DỤ LỊCH SỬ THANH TOÁN:

| id   | school        | plan  | amount      | vnpay_txn        | status  | paid_at             |
|------|---------------|-------|-------------|------------------|---------|---------------------|
| p001 | THPT LHP      | Pro   | 50,000,000  | VNP20260101123  | PAID    | 2026-01-01 10:30    |
| p002 | THPT LQĐ      | Basic | 20,000,000  | VNP20260301456  | PAID    | 2026-03-01 14:20    |
| p003 | THPT NVT      | Ent   | 150,000,000 | VNP20260901789  | PENDING | NULL                |
| p004 | THPT LHP      | Pro   | 50,000,000  | VNP20270101234  | PAID    | 2027-01-01 09:15    |

→ Row p004: THPT LHP renew license sau 1 năm`,
        flow: [
            { step: 1, desc: "SCHOOL_MANAGER click 'Mua/Renew License'" },
            { step: 2, desc: "INSERT school_payments (status='PENDING')" },
            { step: 3, desc: "Redirect VNPay với payment_id" },
            { step: 4, desc: "User thanh toán trên VNPay" },
            { step: 5, desc: "VNPay IPN callback → UPDATE status='PAID', vnpay_transaction_id" },
            { step: 6, desc: "UPDATE schools (license_end += 1 year)" }
        ],
        businessRules: [
            "PENDING timeout sau 15 phút → AUTO CANCEL",
            "PAID: Extend license_end",
            "REFUNDED: Revoke license ngay lập tức",
            "Admin có thể manual approve (bypass VNPay cho testing)"
        ]
    },

    {
        name: "class_teacher_assignments",
        dbName: "class_teacher_assignments",
        category: "school",
        categoryName: "Quản lý Trường học",
        purpose: "Phân công giáo viên dạy lớp (M-N: 1 teacher nhiều lớp, 1 lớp nhiều teachers)",
        roles: ["manager", "teacher"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Assignment ID" },
            { name: "class_id", type: "UUID FK", description: "Lớp học" },
            { name: "teacher_id", type: "INTEGER FK", description: "Giáo viên" },
            { name: "subject", type: "VARCHAR(50)", description: "Môn học (Vật lý, Toán, ...)" },
            { name: "active", type: "BOOLEAN", description: "Còn dạy không" }
        ],
        example: `VÍ DỤ - SCHOOL_MANAGER phân công:

Thầy Minh (teacher_id=102) dạy:
- Lớp 10A1: Vật lý
- Lớp 10A2: Vật lý
- Lớp 11B1: Vật lý

Cô Hoa (teacher_id=103) dạy:
- Lớp 10A1: Toán
- Lớp 10A2: Toán

SQL:
INSERT INTO class_teacher_assignments (class_id, teacher_id, subject, school_year)
VALUES
  ('uuid-10a1', 102, 'Vật lý', '2026-2027'),
  ('uuid-10a2', 102, 'Vật lý', '2026-2027'),
  ('uuid-11b1', 102, 'Vật lý', '2026-2027'),
  ('uuid-10a1', 103, 'Toán', '2026-2027'),
  ('uuid-10a2', 103, 'Toán', '2026-2027');`,
        flow: [
            { step: 1, desc: "SCHOOL_MANAGER tạo lớp mới (10A1)" },
            { step: 2, desc: "SCHOOL_MANAGER assign teachers cho lớp" },
            { step: 3, desc: "INSERT class_teacher_assignments" },
            { step: 4, desc: "Teachers login → Thấy classes được assigned" },
            { step: 5, desc: "Teacher chỉ thấy students trong classes của mình" }
        ],
        businessRules: [
            "1 teacher có thể dạy NHIỀU lớp",
            "1 lớp có thể có NHIỀU teachers (môn khác nhau)",
            "PhysLive CHỈ dùng cho môn Vật lý (subject = 'Vật lý')",
            "UNIQUE constraint: (class_id, teacher_id, subject, school_year)"
        ]
    },

    {
        name: "grade_levels",
        dbName: "grade_levels",
        category: "school",
        categoryName: "Quản lý Trường học",
        purpose: "Khối lớp (10, 11, 12) - Reference data cho phân loại nội dung",
        roles: ["admin", "reviewer", "teacher", "student"],
        columns: [
            { name: "id", type: "SERIAL PRIMARY KEY", description: "1, 2, 3" },
            { name: "grade", type: "INTEGER UNIQUE", description: "10, 11, 12" },
            { name: "display_name", type: "VARCHAR(50)", description: "Tên hiển thị" }
        ],
        example: `VÍ DỤ DỮ LIỆU CỐ ĐỊNH:

| id | grade | display_name     | description                    |
|----|-------|------------------|--------------------------------|
| 1  | 10    | Lớp 10 (THPT)    | Chuyển động học, Động lực học  |
| 2  | 11    | Lớp 11 (THPT)    | Sóng cơ, Nhiệt học            |
| 3  | 12    | Lớp 12 (THPT)    | Điện học, Quang học, Vật lý hạt nhân |

DÙNG CHO:
- Filter simulations theo khối
- Filter topics theo khối
- School classes thuộc khối nào`,
        flow: [
            { step: 1, desc: "System init: INSERT 3 grade_levels (10, 11, 12)" },
            { step: 2, desc: "Topics được tag với grade_level_id" },
            { step: 3, desc: "Simulations được tag với grade_level_id" },
            { step: 4, desc: "Students filter nội dung theo khối của mình" }
        ],
        businessRules: [
            "3 grades CỐ ĐỊNH (10, 11, 12)",
            "Không thêm/xóa grades (follow THPT Vietnam curriculum)",
            "Used for content categorization only",
            "Not enforced strictly (teacher có thể dạy cross-grade)"
        ]
    },

    {
        name: "simulation_runs",
        dbName: "simulation_runs",
        category: "teaching",
        categoryName: "Giảng dạy & Học tập",
        purpose: "Log mỗi lần chạy simulation (input/output) - cho grading và analytics",
        roles: ["teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Run ID" },
            { name: "simulation_id", type: "UUID FK", description: "Simulation nào" },
            { name: "user_id", type: "INTEGER FK", description: "Ai chạy" },
            { name: "assignment_id", type: "UUID FK NULL", description: "Trong bài tập nào (NULL = free play)" },
            { name: "input_parameters", type: "JSONB", description: "Input: v0, h0, angle, ..." },
            { name: "output_results", type: "JSONB", description: "Output: t_flight, range, max_height" }
        ],
        example: `VÍ DỤ - Em Hùng chạy simulation "Chuyển động ném ngang":

INPUT:
{
  "v0": 10,        // vận tốc ban đầu (m/s)
  "h0": 20,        // độ cao ban đầu (m)
  "angle": 30,     // góc ném (độ)
  "g": 9.8         // gia tốc trọng trường
}

OUTPUT (AI solver tính toán):
{
  "t_flight": 2.02,      // thời gian bay (s)
  "range": 20.2,         // tầm xa (m)
  "max_height": 21.275,  // độ cao max (m)
  "trajectory": [[0,20], [1,21], [2,19], ...]  // quỹ đạo
}

INSERT INTO simulation_runs (simulation_id, user_id, input_parameters, output_results)
VALUES ('uuid-sim1', 201, '{"v0":10,"h0":20}', '{"t_flight":2.02,"range":20.2}');`,
        flow: [
            { step: 1, desc: "Student mở simulation" },
            { step: 2, desc: "Nhập input: v0=10, h0=20, angle=30" },
            { step: 3, desc: "Click 'Run' → Gọi AI Solver API" },
            { step: 4, desc: "AI trả về output results" },
            { step: 5, desc: "INSERT simulation_runs (log đầy đủ input/output)" },
            { step: 6, desc: "Render visualization (quỹ đạo, đồ thị)" }
        ],
        businessRules: [
            "Mỗi lần chạy = 1 row (để analytics)",
            "assignment_id NULL = free play (không tính điểm)",
            "assignment_id NOT NULL = graded run (trong bài tập)",
            "Token usage tracked (AI API cost)"
        ]
    },

    {
        name: "specifications",
        dbName: "specifications",
        category: "teaching",
        categoryName: "Giảng dạy & Học tập",
        purpose: "Physics problem specs (parsed từ text/image) - input cho solver",
        roles: ["admin", "reviewer", "teacher"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Spec ID" },
            { name: "problem_submission_id", type: "UUID FK", description: "Bài toán gốc" },
            { name: "extracted_text", type: "TEXT", description: "Text đã OCR" },
            { name: "structured_data", type: "JSONB", description: "Parsed JSON" },
            { name: "confirmation_state", type: "VARCHAR(20)", description: "NO_AMBIGUITY, CONFIRMED, REJECTED" }
        ],
        example: `VÍ DỤ - Teacher submit bài toán text:

INPUT TEXT:
"Một vật được ném ngang từ độ cao 20m với vận tốc 10 m/s. 
Bỏ qua sức cản không khí. Tính thời gian vật rơi và tầm xa."

EXTRACTED (AI parsing):
{
  "archetype": "projectile_motion",
  "givens": {
    "h0": {"value": 20, "unit": "m"},
    "v0": {"value": 10, "unit": "m/s"},
    "angle": {"value": 0, "unit": "degree"}  // ném ngang
  },
  "unknowns": ["t_flight", "range"],
  "constraints": ["no_air_resistance"]
}

→ INSERT specifications (structured_data = JSON trên)
→ Dùng để validate hoặc generate simulation`,
        flow: [
            { step: 1, desc: "Teacher paste text hoặc upload image" },
            { step: 2, desc: "AI OCR + Parse → Extract physics parameters" },
            { step: 3, desc: "INSERT specifications (structured_data)" },
            { step: 4, desc: "If ambiguous → REVIEWER review" },
            { step: 5, desc: "If confirmed → Dùng để tạo simulation/validation" }
        ],
        businessRules: [
            "1 problem_submission → 1 specification",
            "AI parsing có thể sai → Cần REVIEWER confirm",
            "confirmation_state = CONFIRMED → Ready to use",
            "REJECTED → Teacher phải sửa input"
        ]
    },

    {
        name: "topics",
        dbName: "topics",
        category: "curriculum",
        categoryName: "Curriculum & Nội dung",
        purpose: "Các chủ đề physics theo SGK (Chuyển động học, Động lực học, ...)",
        roles: ["admin", "reviewer", "teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Topic ID" },
            { name: "name", type: "VARCHAR(120)", description: "Tên chủ đề" },
            { name: "slug", type: "VARCHAR(120) UNIQUE", description: "URL-friendly name" },
            { name: "grade_level_id", type: "INTEGER FK", description: "Khối lớp (10/11/12)" },
            { name: "display_order", type: "INTEGER", description: "Thứ tự hiển thị" }
        ],
        example: `VÍ DỤ TOPICS (theo SGK THPT):

KHỐI 10:
1. Chuyển động học
2. Động lực học
3. Cân bằng và chuyển động quay

KHỐI 11:
4. Sóng cơ
5. Nhiệt học
6. Điện học cơ bản

KHỐI 12:
7. Điện từ học
8. Quang học
9. Vật lý hạt nhân

SQL INSERT:
INSERT INTO topics (name, slug, grade_level_id, display_order)
VALUES 
  ('Chuyển động học', 'chuyen-dong-hoc', 1, 1),
  ('Động lực học', 'dong-luc-hoc', 1, 2),
  ('Sóng cơ', 'song-co', 2, 1);`,
        flow: [
            { step: 1, desc: "ADMIN/REVIEWER tạo topics theo SGK" },
            { step: 2, desc: "Topics có thứ tự (display_order)" },
            { step: 3, desc: "Content modules link vào topics" },
            { step: 4, desc: "Students browse theo topics" },
            { step: 5, desc: "Analytics: Track progress per topic" }
        ],
        businessRules: [
            "Follow SGK THPT Vietnam (Bộ GD&ĐT)",
            "Tạo bởi ADMIN/REVIEWER only",
            "Slug dùng cho URL (VD: /topics/chuyen-dong-hoc)",
            "Display order để sort trong UI"
        ]
    },

    {
        name: "content_modules",
        dbName: "content_modules",
        category: "curriculum",
        categoryName: "Curriculum & Nội dung",
        purpose: "Module nội dung (1 topic có nhiều modules, VD: Topic 'Chuyển động' → Module 'Ném ngang')",
        roles: ["admin", "reviewer", "teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Module ID" },
            { name: "name", type: "VARCHAR(120)", description: "Tên module" },
            { name: "topic_id", type: "UUID FK", description: "Thuộc topic nào" },
            { name: "schema_id", type: "VARCHAR(80)", description: "Schema version ID" },
            { name: "content_json", type: "JSONB", description: "Nội dung lý thuyết + bài tập" }
        ],
        example: `VÍ DỤ - Topic "Chuyển động học" có 3 modules:

1. Module "Chuyển động thẳng đều"
   - Lý thuyết: v = const, s = vt
   - Bài tập mẫu: 5 câu
   - Simulations: 2 simulations

2. Module "Chuyển động ném ngang"
   - Lý thuyết: x = v0*t, y = h0 - 0.5*g*t²
   - Bài tập mẫu: 8 câu
   - Simulations: 4 simulations

3. Module "Chuyển động tròn đều"
   - Lý thuyết: v = ωr, a = v²/r
   - Bài tập mẫu: 6 câu
   - Simulations: 3 simulations`,
        flow: [
            { step: 1, desc: "REVIEWER tạo module mới" },
            { step: 2, desc: "Viết content (theory + exercises) trong JSONB" },
            { step: 3, desc: "Link simulations vào module" },
            { step: 4, desc: "Publish module → INSERT topic_module_releases" },
            { step: 5, desc: "Students học theo modules" }
        ],
        businessRules: [
            "1 topic → nhiều modules (sub-topics)",
            "Module có version (schema_id)",
            "Content_json chứa theory + exercises + simulations",
            "Chỉ REVIEWER mới publish module"
        ]
    },

    {
        name: "lessons",
        dbName: "lessons",
        category: "curriculum",
        categoryName: "Curriculum & Nội dung",
        purpose: "Bài học cụ thể (1 module → nhiều lessons theo chương trình)",
        roles: ["admin", "reviewer", "teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Lesson ID" },
            { name: "module_id", type: "UUID FK", description: "Thuộc module nào" },
            { name: "title", type: "VARCHAR(255)", description: "Tiêu đề bài học" },
            { name: "content", type: "TEXT", description: "Nội dung markdown" },
            { name: "duration_minutes", type: "INTEGER", description: "Thời lượng dự kiến" }
        ],
        example: `VÍ DỤ - Module "Ném ngang" có 3 lessons:

Lesson 1: "Lý thuyết chuyển động ném ngang"
- Duration: 45 phút
- Content: Công thức, ví dụ, video giảng

Lesson 2: "Bài tập ứng dụng"
- Duration: 60 phút
- Content: 10 bài tập có lời giải chi tiết

Lesson 3: "Thực hành simulation"
- Duration: 45 phút
- Content: Hướng dẫn sử dụng 4 simulations`,
        flow: [
            { step: 1, desc: "REVIEWER tạo lessons cho module" },
            { step: 2, desc: "Upload content (markdown + images)" },
            { step: 3, desc: "Students học theo thứ tự lessons" },
            { step: 4, desc: "Track progress: Đã học lesson nào chưa" }
        ],
        businessRules: [
            "1 module → nhiều lessons (chi tiết hơn)",
            "Lessons có thứ tự (lesson_order)",
            "Duration để estimate study time",
            "Students phải học tuần tự (lesson 1 → 2 → 3)"
        ]
    },

    {
        name: "topic_module_releases",
        dbName: "topic_module_releases",
        category: "curriculum",
        categoryName: "Curriculum & Nội dung",
        purpose: "Version releases của modules (giống git tags) - tracking content changes",
        roles: ["admin", "reviewer"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Release ID" },
            { name: "topic", type: "VARCHAR(80)", description: "Topic name" },
            { name: "module_name", type: "VARCHAR(120)", description: "Module name" },
            { name: "schema_id", type: "VARCHAR(80)", description: "Schema version" },
            { name: "lifecycle_status", type: "VARCHAR(16)", description: "DRAFT, APPROVED, PUBLISHED" }
        ],
        example: `VÍ DỤ VERSION HISTORY:

Module "Chuyển động ném ngang":
v1.0 (2026-01-01): Draft - Nội dung ban đầu
v1.1 (2026-02-15): Approved - REVIEWER kiểm duyệt
v1.2 (2026-03-01): Published - Public cho schools
v2.0 (2026-09-01): Published - Thêm 3 simulations mới

→ Schools luôn dùng version PUBLISHED mới nhất`,
        flow: [
            { step: 1, desc: "REVIEWER tạo/sửa module → status = DRAFT" },
            { step: 2, desc: "REVIEWER review nội dung → APPROVED" },
            { step: 3, desc: "ADMIN publish → PUBLISHED" },
            { step: 4, desc: "Schools nhận update tự động" },
            { step: 5, desc: "Old versions archived (không xóa)" }
        ],
        businessRules: [
            "DRAFT: Chỉ REVIEWER thấy",
            "APPROVED: Ready to publish",
            "PUBLISHED: Live for all schools",
            "Không xóa old versions (audit trail)"
        ]
    },

    {
        name: "schema_versions",
        dbName: "schema_versions",
        category: "curriculum",
        categoryName: "Curriculum & Nội dung",
        purpose: "Schema definitions cho physics problems (template cho parsing AI)",
        roles: ["admin", "reviewer"],
        columns: [
            { name: "id", type: "VARCHAR(80) PRIMARY KEY", description: "Schema ID" },
            { name: "major", type: "INTEGER", description: "Major version" },
            { name: "minor", type: "INTEGER", description: "Minor version" },
            { name: "patch", type: "INTEGER", description: "Patch version" },
            { name: "schema_json", type: "JSONB", description: "JSON Schema định nghĩa" }
        ],
        example: `VÍ DỤ SCHEMA v1.2.0 cho "Projectile Motion":

{
  "archetype": "projectile_motion",
  "required_fields": ["v0", "angle"],
  "optional_fields": ["h0", "g"],
  "units": {
    "v0": "m/s",
    "angle": "degree",
    "h0": "m",
    "g": "m/s²"
  },
  "defaults": {
    "h0": 0,
    "g": 9.8
  },
  "validation_rules": {
    "v0": {"min": 0, "max": 1000},
    "angle": {"min": 0, "max": 90}
  }
}`,
        flow: [
            { step: 1, desc: "ADMIN define schema cho problem type mới" },
            { step: 2, desc: "AI parsing sử dụng schema để validate" },
            { step: 3, desc: "Version bump khi có breaking changes" },
            { step: 4, desc: "Old versions vẫn support (backward compat)" }
        ],
        businessRules: [
            "Semantic versioning: major.minor.patch",
            "Breaking changes → major++",
            "New fields → minor++",
            "Bug fixes → patch++",
            "Tất cả versions published"
        ]
    },

    {
        name: "solver_versions",
        dbName: "solver_versions",
        category: "curriculum",
        categoryName: "Curriculum & Nội dung",
        purpose: "Physics solver versions (numerical solver algorithms)",
        roles: ["admin", "reviewer"],
        columns: [
            { name: "id", type: "VARCHAR(24) PRIMARY KEY", description: "Solver ID" },
            { name: "archetype", type: "VARCHAR(40)", description: "Problem type" },
            { name: "solver_type", type: "VARCHAR(20)", description: "NUMERICAL, SYMBOLIC" },
            { name: "config_json", type: "JSONB", description: "Solver config" }
        ],
        example: `VÍ DỤ SOLVER VERSIONS:

1. projectile-v1.0 (NUMERICAL)
   - Runge-Kutta 4th order
   - Step size: 0.01s
   - Accuracy: 1e-6

2. projectile-v2.0 (SYMBOLIC)
   - Analytical solutions
   - Exact answers
   - Faster computation

→ Simulations chọn solver version phù hợp`,
        flow: [
            { step: 1, desc: "ADMIN deploy solver algorithm mới" },
            { step: 2, desc: "INSERT solver_versions" },
            { step: 3, desc: "Simulations reference solver_version_id" },
            { step: 4, desc: "Run simulation → Gọi solver tương ứng" }
        ],
        businessRules: [
            "NUMERICAL: Numerical integration (chậm, chính xác)",
            "SYMBOLIC: Analytical formulas (nhanh, exact)",
            "Mỗi archetype có nhiều solver versions",
            "Default solver = latest PUBLISHED version"
        ]
    },

    {
        name: "benchmark_problems",
        dbName: "benchmark_problems",
        category: "benchmark",
        categoryName: "Benchmark & Quality",
        purpose: "Bộ đề chuẩn để test AI parsing accuracy (gold standard)",
        roles: ["admin", "reviewer"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Problem ID" },
            { name: "problem_text", type: "TEXT", description: "Đề bài text" },
            { name: "problem_image_url", type: "TEXT", description: "Ảnh đề bài" },
            { name: "difficulty", type: "VARCHAR(20)", description: "EASY, MEDIUM, HARD" },
            { name: "tags", type: "TEXT[]", description: "Tags phân loại" }
        ],
        example: `VÍ DỤ BENCHMARK SET (100 problems):

Easy (30 problems):
- Đề rõ ràng, không ambiguous
- VD: "Vật ném ngang v0=10m/s, h0=20m. Tính t?"

Medium (50 problems):
- Có 1-2 ambiguities nhỏ
- VD: "Vật ném từ tháp cao 20m..." (chưa rõ góc)

Hard (20 problems):
- Nhiều ambiguities, thiếu data
- VD: "Vật rơi xuống đất..." (thiếu v0, h0, angle)

→ REVIEWER dùng để test AI parsing quality`,
        flow: [
            { step: 1, desc: "REVIEWER collect 100 problems từ SGK/đề thi" },
            { step: 2, desc: "INSERT benchmark_problems" },
            { step: 3, desc: "REVIEWER manually annotate (gold_annotations)" },
            { step: 4, desc: "Run AI parsing → So sánh với gold" },
            { step: 5, desc: "Calculate accuracy metrics" }
        ],
        businessRules: [
            "Benchmark set FIXED (không thay đổi thường xuyên)",
            "Có ground truth (gold annotations)",
            "Dùng để measure AI improvement",
            "Re-evaluate khi update AI model"
        ]
    },

    {
        name: "gold_annotations",
        dbName: "gold_annotations",
        category: "benchmark",
        categoryName: "Benchmark & Quality",
        purpose: "Ground truth annotations cho benchmark problems (do REVIEWER tạo thủ công)",
        roles: ["admin", "reviewer"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Annotation ID" },
            { name: "benchmark_problem_id", type: "UUID FK", description: "Problem reference" },
            { name: "annotator_id", type: "INTEGER FK", description: "REVIEWER ID" },
            { name: "structured_data", type: "JSONB", description: "Correct parsed JSON" },
            { name: "notes", type: "TEXT", description: "Ghi chú REVIEWER" }
        ],
        example: `VÍ DỤ GOLD ANNOTATION:

Problem: "Vật ném ngang từ h=20m, v0=10m/s. Tính t và x?"

REVIEWER manually annotate:
{
  "archetype": "projectile_motion",
  "givens": {
    "h0": 20,
    "v0": 10,
    "angle": 0     // ném ngang = 0 độ
  },
  "unknowns": ["t_flight", "range"],
  "ambiguities": []  // Không có ambiguity
}

Notes: "Đề rõ ràng, không cần clarification"

→ Đây là đáp án chuẩn để so sánh với AI parsing`,
        flow: [
            { step: 1, desc: "REVIEWER chọn benchmark problem" },
            { step: 2, desc: "REVIEWER manually parse → JSON" },
            { step: 3, desc: "INSERT gold_annotations (ground truth)" },
            { step: 4, desc: "AI parse same problem → AI result" },
            { step: 5, desc: "Compare AI vs Gold → Calculate accuracy" }
        ],
        businessRules: [
            "1 benchmark problem → 1+ gold annotations (consensus)",
            "Nếu 2 REVIEWER disagree → Adjudication",
            "Gold annotations NEVER changed (immutable)",
            "Used for evaluation only (not training)"
        ]
    },

    {
        name: "evaluation_runs",
        dbName: "evaluation_runs",
        category: "benchmark",
        categoryName: "Benchmark & Quality",
        purpose: "Chạy đánh giá AI parsing accuracy trên benchmark set",
        roles: ["admin", "reviewer"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Run ID" },
            { name: "run_name", type: "VARCHAR(120)", description: "Tên đợt eval" },
            { name: "model_version", type: "VARCHAR(40)", description: "AI model version" },
            { name: "accuracy", type: "DECIMAL(5,4)", description: "Overall accuracy" },
            { name: "f1_score", type: "DECIMAL(5,4)", description: "F1 score" }
        ],
        example: `VÍ DỤ EVALUATION HISTORY:

| run_name          | model_version | accuracy | f1_score | run_date   |
|-------------------|---------------|----------|----------|------------|
| Baseline GPT-3.5  | gpt-3.5-turbo | 0.7200   | 0.6850   | 2026-01-01 |
| Fine-tuned v1     | ft-001        | 0.8350   | 0.8100   | 2026-03-15 |
| Fine-tuned v2     | ft-002        | 0.8920   | 0.8750   | 2026-06-20 |
| GPT-4 Baseline    | gpt-4-turbo   | 0.9150   | 0.9000   | 2026-09-10 |

→ Track AI improvement over time`,
        flow: [
            { step: 1, desc: "ADMIN trigger evaluation run" },
            { step: 2, desc: "Loop: Parse all 100 benchmark problems" },
            { step: 3, desc: "Compare AI results vs gold_annotations" },
            { step: 4, desc: "Calculate metrics (accuracy, F1, precision, recall)" },
            { step: 5, desc: "INSERT evaluation_runs với results" }
        ],
        businessRules: [
            "Run khi có model update mới",
            "Track improvement qua các versions",
            "Accuracy target: > 90%",
            "F1 score target: > 85%"
        ]
    },

    {
        name: "adjudications",
        dbName: "adjudications",
        category: "benchmark",
        categoryName: "Benchmark & Quality",
        purpose: "Giải quyết conflicts khi 2 REVIEWER disagree trên gold annotation",
        roles: ["admin", "reviewer"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Adjudication ID" },
            { name: "benchmark_problem_id", type: "UUID FK", description: "Problem bị tranh cãi" },
            { name: "reviewer1_id", type: "INTEGER FK", description: "REVIEWER 1" },
            { name: "reviewer2_id", type: "INTEGER FK", description: "REVIEWER 2" },
            { name: "final_annotation", type: "JSONB", description: "Annotation cuối cùng" }
        ],
        example: `VÍ DỤ CONFLICT RESOLUTION:

Problem: "Vật rơi từ tầng cao xuống đất sau 2s"

REVIEWER 1 annotate:
{
  "h0": "unknown",  // Chưa biết độ cao
  "t": 2
}

REVIEWER 2 annotate:
{
  "h0": 19.6,  // Tính từ h = 0.5*g*t² = 19.6m
  "t": 2
}

→ CONFLICT! ADMIN adjudicate:
Final annotation: Follow REVIEWER 2 (h0 có thể suy ra)`,
        flow: [
            { step: 1, desc: "2 REVIEWER annotate independently" },
            { step: 2, desc: "System detect disagreement (JSON diff)" },
            { step: 3, desc: "INSERT adjudications (status=PENDING)" },
            { step: 4, desc: "ADMIN review → Choose final annotation" },
            { step: 5, desc: "UPDATE adjudications (status=RESOLVED)" }
        ],
        businessRules: [
            "ADMIN làm tie-breaker (quyết định cuối cùng)",
            "Final annotation = ground truth",
            "Update gold_annotations với final result",
            "Conflict rate target: < 10%"
        ]
    },

    {
        name: "ambiguity_cases",
        dbName: "ambiguity_cases",
        category: "ambiguity",
        categoryName: "Ambiguity Handling",
        purpose: "Log các trường hợp đề bài ambiguous (thiếu thông tin, mơ hồ)",
        roles: ["reviewer", "teacher"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Case ID" },
            { name: "problem_submission_id", type: "UUID FK", description: "Problem gốc" },
            { name: "specification_id", type: "UUID FK", description: "Parsed spec" },
            { name: "ambiguity_description", type: "TEXT", description: "Mô tả ambiguity" },
            { name: "status", type: "VARCHAR(20)", description: "OPEN, RESOLVED, ESCALATED" }
        ],
        example: `VÍ DỤ AMBIGUITY CASES:

Case 1: "Vật ném từ tháp cao xuống đất"
Ambiguity: Thiếu h0 (độ cao tháp)
Status: OPEN → Teacher phải clarify

Case 2: "Vật ném với vận tốc lớn"
Ambiguity: "Lớn" là bao nhiêu? (v0 không rõ)
Status: RESOLVED → Teacher sửa thành v0=20m/s

Case 3: "Vật chuyển động trên mặt phẳng nghiêng"
Ambiguity: Thiếu góc nghiêng α
Status: ESCALATED → REVIEWER review`,
        flow: [
            { step: 1, desc: "AI detect ambiguity trong parsing" },
            { step: 2, desc: "INSERT ambiguity_cases (status=OPEN)" },
            { step: 3, desc: "Teacher receive notification" },
            { step: 4, desc: "Teacher clarify → UPDATE status=RESOLVED" },
            { step: 5, desc: "Nếu phức tạp → ESCALATED to REVIEWER" }
        ],
        businessRules: [
            "Auto-detect bởi AI",
            "Teacher phải resolve trong 48h",
            "ESCALATED → REVIEWER intervene",
            "Track ambiguity rate để improve AI"
        ]
    },

    {
        name: "reviewer_decisions",
        dbName: "reviewer_decisions",
        category: "ambiguity",
        categoryName: "Ambiguity Handling",
        purpose: "Quyết định của REVIEWER cho ambiguity cases (confirm/reject/edit)",
        roles: ["reviewer"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Decision ID" },
            { name: "ambiguity_case_id", type: "UUID FK", description: "Case reference" },
            { name: "reviewer_id", type: "INTEGER FK", description: "REVIEWER ID" },
            { name: "decision", type: "VARCHAR(20)", description: "CONFIRM, REJECT, EDIT" },
            { name: "edited_specification", type: "JSONB", description: "Spec sau khi sửa" }
        ],
        example: `VÍ DỤ REVIEWER DECISIONS:

Case: "Vật ném từ tháp cao"

REVIEWER Decision:
{
  "decision": "EDIT",
  "edited_specification": {
    "h0": {"value": 20, "unit": "m", "note": "Giả sử tháp cao 20m"}
  },
  "rationale": "Đề SGK lớp 10 thường dùng h0=20m"
}

→ Teacher nhận spec đã được REVIEWER fix`,
        flow: [
            { step: 1, desc: "REVIEWER nhận ambiguity case" },
            { step: 2, desc: "REVIEWER analyze problem" },
            { step: 3, desc: "REVIEWER decide: CONFIRM/REJECT/EDIT" },
            { step: 4, desc: "INSERT reviewer_decisions" },
            { step: 5, desc: "UPDATE ambiguity_cases status = RESOLVED" }
        ],
        businessRules: [
            "CONFIRM: Spec đúng, không có ambiguity",
            "REJECT: Đề sai hoàn toàn, không thể sửa",
            "EDIT: Sửa spec → Ready to use",
            "REVIEWER decisions immutable (audit trail)"
        ]
    },

    {
        name: "extraction_runs",
        dbName: "extraction_runs",
        category: "extraction",
        categoryName: "Text/Image Extraction",
        purpose: "Log extraction attempts (OCR + AI parsing) từ text/image → JSON",
        roles: ["reviewer", "teacher"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Run ID" },
            { name: "problem_submission_id", type: "UUID FK", description: "Problem input" },
            { name: "extraction_path", type: "VARCHAR(20)", description: "OPENROUTER, RULE_BASED" },
            { name: "status", type: "VARCHAR(20)", description: "RUNNING, SUCCEEDED, FAILED" },
            { name: "outcome", type: "VARCHAR(20)", description: "API_SUCCESS, FALLBACK, FAILED" }
        ],
        example: `VÍ DỤ EXTRACTION WORKFLOW:

Attempt 1: OPENROUTER (AI API)
- Status: RUNNING
- Outcome: API_SUCCESS
- Extracted: {"v0": 10, "h0": 20}

Attempt 2: OPENROUTER (AI timeout)
- Status: FAILED
- Outcome: API_TIMEOUT
- Fallback: RULE_BASED

Attempt 3: RULE_BASED (regex parsing)
- Status: SUCCEEDED
- Outcome: RULE_BASED_FALLBACK
- Extracted: {"v0": 10} (thiếu h0)`,
        flow: [
            { step: 1, desc: "Teacher submit problem (text/image)" },
            { step: 2, desc: "INSERT extraction_runs (status=RUNNING)" },
            { step: 3, desc: "Try OPENROUTER API first" },
            { step: 4, desc: "If success → outcome=API_SUCCESS" },
            { step: 5, desc: "If fail → Fallback RULE_BASED" },
            { step: 6, desc: "UPDATE extraction_runs với result" }
        ],
        businessRules: [
            "OPENROUTER first (high accuracy)",
            "RULE_BASED fallback (lower accuracy but reliable)",
            "Track success rate per path",
            "Retry logic: 3 attempts max"
        ]
    },

    {
        name: "source_assets",
        dbName: "source_assets",
        category: "extraction",
        categoryName: "Text/Image Extraction",
        purpose: "Lưu trữ text/image gốc của problems (before extraction)",
        roles: ["reviewer", "teacher"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Asset ID" },
            { name: "problem_submission_id", type: "UUID FK", description: "Problem reference" },
            { name: "asset_type", type: "VARCHAR(10)", description: "TEXT, IMAGE" },
            { name: "original_text", type: "TEXT", description: "Raw text" },
            { name: "image_url", type: "TEXT", description: "Image URL (S3)" },
            { name: "ocr_status", type: "VARCHAR(20)", description: "NOT_REQUESTED, SUCCEEDED, FAILED" }
        ],
        example: `VÍ DỤ SOURCE ASSETS:

Asset 1 (TEXT):
{
  "asset_type": "TEXT",
  "original_text": "Vật ném ngang v0=10m/s từ h0=20m",
  "ocr_status": "NOT_REQUESTED"
}

Asset 2 (IMAGE):
{
  "asset_type": "IMAGE",
  "image_url": "s3://bucket/problem_123.jpg",
  "ocr_text": "Vật ném ngang...",
  "ocr_status": "SUCCEEDED"
}`,
        flow: [
            { step: 1, desc: "Teacher upload text/image" },
            { step: 2, desc: "INSERT source_assets" },
            { step: 3, desc: "If IMAGE → Trigger OCR" },
            { step: 4, desc: "UPDATE ocr_text, ocr_status" },
            { step: 5, desc: "Pass to extraction pipeline" }
        ],
        businessRules: [
            "TEXT: Direct parsing (no OCR)",
            "IMAGE: OCR first → Then parse",
            "OCR provider: OpenRouter Vision API",
            "Store original assets (audit/debugging)"
        ]
    },

    {
        name: "parameter_snapshots",
        dbName: "parameter_snapshots",
        category: "extraction",
        categoryName: "Text/Image Extraction",
        purpose: "Snapshot của extracted parameters (immutable history)",
        roles: ["reviewer", "teacher"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Snapshot ID" },
            { name: "specification_id", type: "UUID FK", description: "Spec reference" },
            { name: "snapshot_data", type: "JSONB", description: "Frozen JSON" },
            { name: "created_at", type: "TIMESTAMP", description: "Snapshot time" }
        ],
        example: `VÍ DỤ PARAMETER SNAPSHOTS (version history):

Snapshot 1 (2026-01-01 10:00):
{
  "v0": 10,
  "h0": null  // Chưa có h0
}

Snapshot 2 (2026-01-01 10:05):
{
  "v0": 10,
  "h0": 20  // REVIEWER thêm h0
}

Snapshot 3 (2026-01-01 10:10):
{
  "v0": 15,  // Teacher sửa v0
  "h0": 20
}

→ Track changes qua thời gian`,
        flow: [
            { step: 1, desc: "Extraction complete → CREATE snapshot" },
            { step: 2, desc: "INSERT parameter_snapshots (immutable)" },
            { step: 3, desc: "REVIEWER edit spec → NEW snapshot" },
            { step: 4, desc: "Teacher edit spec → NEW snapshot" },
            { step: 5, desc: "Never UPDATE old snapshots (append-only)" }
        ],
        businessRules: [
            "Immutable (never update/delete)",
            "Every change = new snapshot",
            "Used for audit trail",
            "Can rollback to previous snapshot"
        ]
    },

    {
        name: "support_items",
        dbName: "support_items",
        category: "support",
        categoryName: "Hỗ trợ & Feedback",
        purpose: "Support tickets + feedback từ users (bug reports, feature requests)",
        roles: ["admin", "reviewer", "teacher", "student"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Item ID" },
            { name: "user_id", type: "INTEGER FK", description: "User gửi" },
            { name: "kind", type: "VARCHAR(16)", description: "FEEDBACK, MESSAGE" },
            { name: "status", type: "VARCHAR(20)", description: "OPEN, READ, IN_PROGRESS, RESOLVED" },
            { name: "title", type: "VARCHAR(255)", description: "Tiêu đề" },
            { name: "content", type: "TEXT", description: "Nội dung chi tiết" }
        ],
        example: `VÍ DỤ SUPPORT ITEMS:

Ticket #1 (FEEDBACK):
- User: Teacher Minh
- Title: "Simulation ném ngang chạy chậm"
- Status: RESOLVED
- Response: "Đã optimize solver v2.0"

Ticket #2 (MESSAGE):
- User: Student Hùng
- Title: "Làm sao để nộp bài tập?"
- Status: RESOLVED
- Response: "Click 'Submit' sau khi chạy simulation"`,
        flow: [
            { step: 1, desc: "User click 'Support' → Open form" },
            { step: 2, desc: "Fill title + content + kind" },
            { step: 3, desc: "INSERT support_items (status=OPEN)" },
            { step: 4, desc: "ADMIN nhận notification" },
            { step: 5, desc: "ADMIN reply → UPDATE status=RESOLVED" }
        ],
        businessRules: [
            "FEEDBACK: Bug reports, feature requests",
            "MESSAGE: Questions, help requests",
            "SLA: Respond trong 48h",
            "Auto-close sau 14 ngày no activity"
        ]
    },

    {
        name: "student_action_logs",
        dbName: "student_action_logs",
        category: "support",
        categoryName: "Hỗ trợ & Feedback",
        purpose: "Log hành động của students để analytics và troubleshooting",
        roles: ["admin", "teacher", "student"],
        columns: [
            { name: "id", type: "BIGSERIAL PRIMARY KEY", description: "Log ID" },
            { name: "student_id", type: "INTEGER FK", description: "Student ID" },
            { name: "action_type", type: "VARCHAR(40)", description: "LOGIN, RUN_SIMULATION, SUBMIT_ASSIGNMENT, ..." },
            { name: "metadata", type: "JSONB", description: "Context data" },
            { name: "created_at", type: "TIMESTAMP", description: "Thời gian" }
        ],
        example: `VÍ DỤ STUDENT ACTIVITY LOG:

2026-09-17 08:30 - LOGIN {"ip": "123.45.67.89"}
2026-09-17 08:35 - VIEW_ASSIGNMENT {"assignment_id": "uuid-001"}
2026-09-17 08:40 - RUN_SIMULATION {"simulation_id": "uuid-sim1", "params": {"v0": 10}}
2026-09-17 08:45 - SUBMIT_ASSIGNMENT {"assignment_id": "uuid-001", "score": 18}
2026-09-17 08:50 - LOGOUT {}

→ Teacher xem activity log để understand student behavior`,
        flow: [
            { step: 1, desc: "Student perform action (login, run sim, ...)" },
            { step: 2, desc: "INSERT student_action_logs" },
            { step: 3, desc: "Teacher view logs → See student activity" },
            { step: 4, desc: "Analytics: Calculate engagement metrics" },
            { step: 5, desc: "Retention: Weekly log cleanup (> 90 days)" }
        ],
        businessRules: [
            "Log tất cả major actions (không log minor UI clicks)",
            "Retention: 90 days (GDPR compliance)",
            "Aggregated metrics (daily/weekly)",
            "Privacy: Không log sensitive data"
        ]
    },

    {
        name: "token_usage_audits",
        dbName: "token_usage_audits",
        category: "misc",
        categoryName: "Khác",
        purpose: "Track AI token usage per school (billing + quota management)",
        roles: ["admin", "manager"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Audit ID" },
            { name: "school_id", type: "UUID FK", description: "Trường sử dụng" },
            { name: "operation_type", type: "VARCHAR(40)", description: "SIMULATION_RUN, EXTRACTION, OCR" },
            { name: "tokens_used", type: "INTEGER", description: "Tokens consumed" },
            { name: "cost_usd", type: "DECIMAL(10,6)", description: "Chi phí USD" }
        ],
        example: `VÍ DỤ TOKEN USAGE (Tháng 9/2026 - THPT LHP):

| operation_type  | tokens_used | cost_usd | count |
|-----------------|-------------|----------|-------|
| SIMULATION_RUN  | 1,234       | 0.123    | 50    |
| EXTRACTION      | 3,456       | 0.456    | 20    |
| OCR             | 789         | 0.089    | 10    |
| TOTAL           | 5,479       | 0.668    | 80    |

→ Quota: 8,000 tokens/month → Còn 2,521 (31.5%)
→ Show warning: "Bạn đã dùng 68.5% quota tháng này"`,
        flow: [
            { step: 1, desc: "Teacher chạy simulation → AI API call" },
            { step: 2, desc: "AI API return: tokens_used = 150" },
            { step: 3, desc: "INSERT token_usage_audits" },
            { step: 4, desc: "UPDATE schools.used_tokens += 150" },
            { step: 5, desc: "Check: used_tokens >= quota? → Block new requests" }
        ],
        businessRules: [
            "Track per operation type (granular billing)",
            "Reset used_tokens ngày 1 hàng tháng",
            "Warning at 80% quota usage",
            "Block at 100% quota (soft limit: 105%)"
        ]
    },

    {
        name: "validation_runs",
        dbName: "validation_runs",
        category: "misc",
        categoryName: "Khác",
        purpose: "Validation runs để verify physics accuracy của simulations",
        roles: ["admin", "reviewer", "teacher"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Run ID" },
            { name: "simulation_id", type: "UUID FK", description: "Simulation cần validate" },
            { name: "specification_id", type: "UUID FK", description: "Spec reference" },
            { name: "status", type: "VARCHAR(20)", description: "PENDING, RUNNING, PASSED, FAILED" },
            { name: "validation_results", type: "JSONB", description: "Chi tiết kết quả" }
        ],
        example: `VÍ DỤ VALIDATION RUN:

Simulation: "Chuyển động ném ngang"
Specification: v0=10, h0=20, angle=0

VALIDATION CHECKS:
✅ Energy conservation: PASSED (ΔE < 0.01%)
✅ Momentum conservation: PASSED
✅ Trajectory shape: PASSED (parabola)
✅ Landing time: PASSED (t = 2.02s ± 0.01)
❌ Range accuracy: FAILED (expected 20.2m, got 19.8m)

→ Status: FAILED
→ Teacher phải fix simulation`,
        flow: [
            { step: 1, desc: "Teacher create simulation" },
            { step: 2, desc: "System auto-trigger validation" },
            { step: 3, desc: "INSERT validation_runs (status=PENDING)" },
            { step: 4, desc: "Run validation suite (physics checks)" },
            { step: 5, desc: "UPDATE status = PASSED/FAILED" },
            { step: 6, desc: "If PASSED → simulation.status = READY" }
        ],
        businessRules: [
            "Auto-validation khi tạo simulation mới",
            "PASSED: Physics đúng → Ready to use",
            "FAILED: Có lỗi → Teacher phải fix",
            "Re-validate sau mỗi lần edit simulation"
        ]
    },

    {
        name: "problem_submissions",
        dbName: "problem_submissions",
        category: "misc",
        categoryName: "Khác",
        purpose: "Submissions của problems từ teachers (text/image input) cho AI parsing",
        roles: ["reviewer", "teacher"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Submission ID" },
            { name: "submitter_id", type: "INTEGER FK", description: "Teacher ID" },
            { name: "source_mode", type: "VARCHAR(10)", description: "TEXT, PASTE, IMAGE" },
            { name: "status", type: "VARCHAR(20)", description: "DRAFT, OCR_READY, EXTRACTED, VALIDATED" },
            { name: "title", type: "VARCHAR(255)", description: "Tiêu đề" }
        ],
        example: `VÍ DỤ PROBLEM SUBMISSION WORKFLOW:

Step 1: Teacher submit
- source_mode: TEXT
- status: DRAFT
- title: "Bài tập ném ngang"

Step 2: System extract
- status: EXTRACTED
- specification_id: uuid-spec-001

Step 3: Validation
- status: VALIDATED
- Can use for simulation

Step 4: Publish
- Create simulation from spec`,
        flow: [
            { step: 1, desc: "Teacher submit problem (text/image)" },
            { step: 2, desc: "INSERT problem_submissions (status=DRAFT)" },
            { step: 3, desc: "If IMAGE → OCR → status=OCR_READY" },
            { step: 4, desc: "AI parse → status=EXTRACTED" },
            { step: 5, desc: "Validation → status=VALIDATED" },
            { step: 6, desc: "Teacher create simulation từ spec" }
        ],
        businessRules: [
            "TEXT: Direct parsing",
            "IMAGE: OCR first",
            "PASTE: Copy from clipboard",
            "DRAFT có thể edit, sau đó readonly"
        ]
    },

    {
        name: "library_moderation_audits",
        dbName: "library_moderation_audits",
        category: "library",
        categoryName: "Thư viện",
        purpose: "Audit trail cho moderation actions (REVIEWER approve/reject library items)",
        roles: ["admin", "reviewer"],
        columns: [
            { name: "id", type: "UUID PRIMARY KEY", description: "Audit ID" },
            { name: "library_item_id", type: "UUID FK", description: "Item bị review" },
            { name: "reviewer_id", type: "INTEGER FK", description: "REVIEWER ID" },
            { name: "from_status", type: "VARCHAR(16)", description: "Status trước" },
            { name: "to_status", type: "VARCHAR(16)", description: "Status sau" },
            { name: "comment", type: "TEXT", description: "Ghi chú REVIEWER" }
        ],
        example: `VÍ DỤ MODERATION AUDIT TRAIL:

Library Item: "Chuyển động ném ngang - Thầy Minh"

Audit 1 (2026-09-01):
- from_status: NULL
- to_status: PENDING
- reviewer: NULL
- comment: "Teacher submit to community"

Audit 2 (2026-09-02):
- from_status: PENDING
- to_status: APPROVED
- reviewer: Reviewer Hùng
- comment: "Simulation chất lượng tốt, physics chính xác"

Audit 3 (2026-12-01):
- from_status: APPROVED
- to_status: FEATURED
- reviewer: Admin
- comment: "Promote to featured (high quality)"`,
        flow: [
            { step: 1, desc: "Teacher submit to community → INSERT audit (to_status=PENDING)" },
            { step: 2, desc: "REVIEWER review → INSERT audit (to_status=APPROVED/REJECTED)" },
            { step: 3, desc: "Admin feature → INSERT audit (to_status=FEATURED)" },
            { step: 4, desc: "History immutable (never update/delete audits)" }
        ],
        businessRules: [
            "Append-only (immutable audit trail)",
            "Every status change = new audit row",
            "REVIEWER comment required for REJECTED",
            "Used for compliance and debugging"
        ]
    }
];

// Render functions
function renderTables() {
    const grid = document.getElementById('tablesGrid');
    grid.innerHTML = tables.map(table => `
        <div class="table-card cat-${table.category}" data-roles="${table.roles.join(',')}" data-name="${table.name.toLowerCase()}" onclick="showDetail('${table.name}')">
            <div class="table-name">${table.name}</div>
            <div class="table-db-name">${table.dbName}</div>
            <div class="table-category">${table.categoryName}</div>
            <div class="table-purpose">${table.purpose}</div>
            <div class="roles-list">
                ${table.roles.map(role => `<span class="role-badge role-${role}">${roleLabels[role]}</span>`).join('')}
            </div>
        </div>
    `).join('');
}

// Search
document.getElementById('searchBox').addEventListener('input', function(e) {
    const searchTerm = e.target.value.toLowerCase();
    const cards = document.querySelectorAll('.table-card');
    
    cards.forEach(card => {
        const name = card.dataset.name;
        if (name.includes(searchTerm)) {
            card.classList.remove('hidden');
        } else {
            card.classList.add('hidden');
        }
    });
});

// Filter by role
document.querySelectorAll('.filter-btn').forEach(btn => {
    btn.addEventListener('click', function() {
        document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
        this.classList.add('active');

        const filter = this.dataset.filter;
        const cards = document.querySelectorAll('.table-card');

        cards.forEach(card => {
            if (filter === 'all') {
                card.classList.remove('hidden');
            } else {
                const roles = card.dataset.roles.split(',');
                if (roles.includes(filter)) {
                    card.classList.remove('hidden');
                } else {
                    card.classList.add('hidden');
                }
            }
        });
    });
});

// Show detail modal
function showDetail(tableName) {
    const table = tables.find(t => t.name === tableName);
    if (!table) return;

    const modal = document.getElementById('detailModal');
    const modalBody = document.getElementById('modalBody');

    modalBody.innerHTML = `
        <h2 class="modal-title">${table.name}</h2>
        <div class="modal-db-name">Bảng: ${table.dbName}</div>

        <div class="detail-section">
            <h3>📋 Mục Đích</h3>
            <p>${table.purpose}</p>
        </div>

        <div class="detail-section">
            <h3>👥 Được Sử Dụng Bởi</h3>
            <div class="roles-list">
                ${table.roles.map(role => `<span class="role-badge role-${role}">${roleLabels[role]}</span>`).join('')}
            </div>
        </div>

        <div class="detail-section">
            <h3>🗂️ Các Cột</h3>
            <table class="schema-table">
                <thead>
                    <tr>
                        <th>Tên Cột</th>
                        <th>Kiểu Dữ Liệu</th>
                        <th>Mô Tả</th>
                    </tr>
                </thead>
                <tbody>
                    ${table.columns.map(col => `
                        <tr>
                            <td><strong>${col.name}</strong></td>
                            <td><code>${col.type}</code></td>
                            <td>${col.description}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>

        ${table.example ? `
            <div class="detail-section">
                <h3>💡 Ví Dụ Cụ Thể</h3>
                <div class="example-box">${table.example}</div>
            </div>
        ` : ''}

        ${table.flow ? `
            <div class="detail-section">
                <h3>🔄 Luồng Dữ Liệu</h3>
                <div class="flow-diagram">
                    ${table.flow.map(f => `
                        <div class="flow-step">
                            <div class="flow-step-number">${f.step}</div>
                            <div class="flow-step-content">${f.desc}</div>
                        </div>
                        ${f.step < table.flow.length ? '<div class="flow-arrow">↓</div>' : ''}
                    `).join('')}
                </div>
            </div>
        ` : ''}

        ${table.businessRules ? `
            <div class="detail-section">
                <h3>⚖️ Quy Tắc Nghiệp Vụ</h3>
                <ul class="business-rules">
                    ${table.businessRules.map(rule => `<li>${rule}</li>`).join('')}
                </ul>
            </div>
        ` : ''}
    `;

    modal.classList.add('active');
}

function closeModal() {
    document.getElementById('detailModal').classList.remove('active');
}

// Close modal on outside click
document.getElementById('detailModal').addEventListener('click', function(e) {
    if (e.target === this) {
        closeModal();
    }
});

// Initialize
renderTables();
