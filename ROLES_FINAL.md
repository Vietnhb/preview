# PhysLive account roles

Confirmed 01/10/2026. This is the current role contract for the database, backend and frontend.

## Canonical database identities

1. `ADMIN`: view all user account information; create, edit, suspend/restore and reset passwords for `MANAGER` accounts.
2. `MANAGER`: operate the platform, manage schools and accounts, curriculum, licenses, payments, support and validation. Inherits the former platform administrator's operational permissions.
3. `REVIEWER`: per-account edit permission manages physics contexts, schemas, reference solvers, modules and benchmarks; per-account review permission moderates PUBLIC simulations. An account may have either permission or both, and must have at least one.
4. `SCHOOL`: manage staff, students, classes, school reports and billing within its own school.
5. `STAFF`: teacher; create simulations, manage the personal library, assign and assess learning activities. `staff_type=DEPARTMENT_HEAD` adds own-school teacher/student assignments, school assignment overview and SHARED content moderation. `staff_alias` in the database is generated as `Trưởng bộ môn`; regular `TEACHER` staff receive `Giáo viên`.
6. `STUDENT`: access assigned learning activities, predictions, submissions and shared class content.

Role names are uppercase. IDs are fixed from 1 to 6. No legacy role aliases are accepted by application authorization.

## Account and permission boundaries

`ADMIN`, `MANAGER` and `REVIEWER` are platform accounts with no school. `SCHOOL`, `STAFF` and `STUDENT` require a school. At most one active `SCHOOL` account may belong to each school.

`ADMIN` has no platform-operation, review, teaching or billing permissions. It retains access to its own profile and password. Its management interface lists all users and manages MANAGER accounts only.

Only `ADMIN` creates `MANAGER` accounts. `MANAGER` cannot create or promote an account to `ADMIN` or promote another role to `MANAGER`, and cannot edit or suspend an `ADMIN`. Managers may maintain existing managers. A user cannot change their own role or suspend their own account.

Role checks are enforced by backend request authorization and account-service validation. Frontend routes, navigation and form choices mirror those checks. School-scoped operations still validate the target school and the current license.

Only `SCHOOL` creates classes. Department heads can assign teachers and students to existing classes in their school and inspect school assignments. They cannot create or reset accounts, moderate PUBLIC content, review another school or approve their own simulations. REVIEWER moderation is limited to PUBLIC content. Switching a simulation between school and public sharing requires fresh moderation.

Created/imported/reset accounts have `must_change_password=true`. Until a new password is saved, the backend permits only current-user read, password change and logout. Initial credentials may be handed over as CSV, while database passwords remain hashes. Existing users retain their passwords and are not forced to change by the migration. User profiles and managed-user forms support date of birth and avatar URL.

School bulk imports cover users, classes, enrollments and teacher assignments. Preview validates the entire file; rows may be edited and revalidated before an atomic commit. Identical name and birth date produce a warning and remain valid when email differs. Repeated email is rejected. See `docs/implementation/school-imports.md`.

Active licenses may renew the same plan or immediately upgrade to a configured plan with no lower entitlement quotas and a higher annual price. Paid upgrades replace the current plan and clear any former queued plan. Downgrades and incomparable active plan changes are rejected. Eligibility and reasons come from the server; plan codes do not define a hardcoded rank.

## Migration and deployment

Flyway `V37__replace_account_roles.sql` replaces the old role catalog and updates user foreign keys in one transaction. Existing platform administrators become `MANAGER`; former school managers become `SCHOOL`; former teachers become `STAFF`. Reviewers and students retain their responsibilities with the new IDs. User IDs, school associations and all other user fields are preserved.

The migration updates the role-school validation function, its trigger, the single-school-account index and the fixed role ID/name constraint. Existing Flyway migrations remain unchanged to preserve their recorded checksums. The bootstrap SQL uses the same canonical catalog.

After V37, the confirmed account assignment restores the two original administrator accounts (user IDs 1 and 17) to `ADMIN` (role ID 1). They keep their existing credentials and can create new `MANAGER` accounts. This account-data correction does not change the applied migration or its checksum. No other account data or role assignments are changed; currently no account has the `MANAGER` role.

Migration `V38__account_capabilities_and_initial_password.sql` adds account capabilities, generated staff aliases and the first-login password flag. It gives existing REVIEWER accounts both permissions and existing STAFF accounts the ordinary teacher type. V38 was applied and validated on the configured database on 01/10/2026; the two ADMIN accounts remain unchanged.

Deploy the updated backend and frontend together through migration V38. Existing JWT claims do not override a user's new role: the backend resolves the current role from the database on each authenticated request. Reload the frontend after deployment so its session profile and navigation use the new role.

## Verification

Backend tests cover the six-role API authorization matrix and restrictions on manager creation, role promotion and ADMIN account changes. Frontend role tests cover IDs, permissions, form options and rejection of obsolete role names. The database migration can be rehearsed in a transaction and rolled back to verify every user's mapping and unchanged non-role fields.

## Retained implementation details

The earlier implementation notes below are retained for their school and learning workflows. The canonical IDs and ADMIN/MANAGER account-management restrictions above take precedence over earlier plans.

# ROLES & PERMISSIONS - PHYSLIVE B2B (FINAL VERSION)

> Confirmed 19/09/2026: school accounts are provisioned by managers (no public signup). AI quota is actual provider tokens; finish and charge an in-flight call fully, then block subsequent calls when exhausted. See B2B_IMPLEMENTATION_SUMMARY.md for implemented scope and verification limits.


**Date:** 17/09/2026  
**Status:** ✅ APPROVED - Ready for Implementation

---

## 🎯 **6 ROLES - FINAL**

```
┌─────────────────────────────────────────────────────────┐
│              PLATFORM LEVEL                             │
│                                                         │
│  2. MANAGER               (Quản trị viên platform)        │
│     • school_id = NULL                                  │
│     • Quản lý toàn platform                             │
│                                                         │
│  3. REVIEWER    (Chuyên gia nội dung & Kiểm duyệt)  │
│     • school_id = NULL                                  │
│     • Curriculum Expert - Curriculum Designer           │
│     • TẠO topic schema (FR-REV-01)                     │
│       → Định nghĩa cấu trúc kiến thức (Kinematics,    │
│         Dynamics, Waves...)                             │
│     • TẠO reference solver (FR-REV-02)                 │
│       → Mã giải thuật chuẩn cho từng topic             │
│     • TẠO benchmark problems (FR-REV-06)               │
│       → Bài tập chuẩn để đánh giá AI solver           │
│     • DUYỆT Shared Library do Teacher submit           │
│       → Teacher tạo simulation → REVIEWER approve      │
│     • Phân xử extraction mơ hồ (FR-REV-03)             │
│       → Handle ambiguous natural language              │
│     • KHÔNG tạo simulation cho teaching                 │
│       → Teacher role responsible for teaching content  │
│     • KHÔNG giao bài / chấm bài                         │
│       → Teaching operations only for Teacher           │
│                                                         │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              SCHOOL LEVEL                               │
│                                                         │
│  4. SCHOOL      (Quản lý trường)                │
│     • school_id = <UUID>                                │
│     • CHỈ 1 người/trường                                │
│     • Quản lý users & classes                           │
│     • Xem báo cáo TỔNG HỢP (không chi tiết HS)         │
│     • Phân giáo viên vào lớp                            │
│     • Lớp có SẴN học sinh tương ứng                    │
│                                                         │
│  5. STAFF             (Giáo viên)                     │
│     • school_id = <UUID>                                │
│     • 1 GV đảm nhiệm NHIỀU LỚP                         │
│     • SCHOOL cho phép vào lớp                  │
│     • Tạo simulations & giao bài                        │
│                                                         │
│  6. STUDENT             (Học sinh)                      │
│     • school_id = <UUID>                                │
│     • MỖI LẦN chỉ ở 1 LỚP                              │
│     • Làm bài & xem điểm                                │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 📊 **PERMISSION MATRIX (CORRECTED)**

| Tính năng | MANAGER | REVIEWER | SCHOOL | STAFF | STUDENT |
|-----------|:-----:|:--------:|:----------:|:-------:|:-------:|
| **Platform Management** |
| Quản lý schools | ✅ | ❌ | ❌ | ❌ | ❌ |
| Quản lý licenses | ✅ | ❌ | ❌ | ❌ | ❌ |
| System analytics | ✅ | ✅ | ❌ | ❌ | ❌ |
| **School Management** |
| Tạo Teacher/Student | ✅ | ❌ | ✅ | ❌ | ❌ |
| Soft delete users | ✅ | ❌ | ✅ | ❌ | ❌ |
| Import CSV users | ✅ | ❌ | ✅ | ❌ | ❌ |
| Tạo lớp (với học sinh sẵn) | ✅ | ❌ | ✅ | ❌ | ❌ |
| Phân giáo viên vào lớp | ✅ | ❌ | ✅ | ❌ | ❌ |
| Xem quota trường | ✅ | ❌ | ✅ | ✅ | ❌ |
| Xem báo cáo tổng hợp | ✅ | ❌ | ✅ | ❌ | ❌ |
| Xem báo cáo chi tiết lớp | ✅ | ❌ | ❌ | ✅ | ❌ |
| **Curriculum Design (Schema/Solver/Benchmark)** |
| Tạo topic schema (FR-REV-01) | ✅ | ✅ | ❌ | ❌ | ❌ |
| Tạo reference solver (FR-REV-02) | ✅ | ✅ | ❌ | ❌ | ❌ |
| Tạo benchmark problems (FR-REV-06) | ✅ | ✅ | ❌ | ❌ | ❌ |
| Phân xử extraction mơ hồ (FR-REV-03) | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Content Moderation** |
| Duyệt Shared Library | ✅ | ✅ | ❌ | ❌ | ❌ |
| Remove simulations | ✅ | ✅ | ❌ | ❌ | ❌ |
| Feature simulations | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Simulation Creation** |
| Tạo simulation (AI) | ✅ | ❌ | ❌ | ✅ | ❌ |
| Edit own simulations | ✅ | ❌ | ❌ | ✅ | ❌ |
| Share to community | ✅ | ❌ | ❌ | ✅* | ❌ |
| Delete own simulations | ✅ | ❌ | ❌ | ✅ | ❌ |
| **Teaching & Learning** |
| Giao bài | ✅ | ❌ | ❌ | ✅ | ❌ |
| Chấm bài | ✅ | ❌ | ❌ | ✅ | ❌ |
| Làm bài | ❌ | ❌ | ❌ | ❌ | ✅ |
| Xem điểm chi tiết | ✅ | ❌ | ❌ | ✅ (class) | ✅ (own) |

*Teacher share → PENDING_REVIEW → REVIEWER approve

---

## 🔧 **KEY FIXES APPLIED:**

### ✅ **Fix 1: REVIEWER role - Simplified**
```
BEFORE:
  ✅ Tạo simulation
  ✅ Giao bài
  ✅ Chấm bài

AFTER:
  ❌ KHÔNG tạo simulation
  ❌ KHÔNG giao bài
  ❌ KHÔNG chấm bài
  ✅ CHỈ review & approve Shared Library
```

### ✅ **Fix 3: SCHOOL - Aggregate Reports Only**
```
SCHOOL xem:
  ✅ Báo cáo tổng hợp:
     - Điểm trung bình lớp
     - Tỷ lệ hoàn thành
     - Số bài đã nộp
  
  ❌ KHÔNG xem:
     - Điểm từng học sinh
     - Chi tiết bài làm
```

### ✅ **Fix 4: Class Assignment Flow**
```
WORKFLOW:

[1] SCHOOL tạo lớp
    • Tên lớp: 10A1
    • Khối: 10
    • Danh sách học sinh: [HS1, HS2, HS3, ...]
    ↓
[2] SCHOOL phân giáo viên
    • Chọn Teacher X
    • Assign vào lớp 10A1
    ↓
[3] Teacher X login
    • Thấy lớp 10A1 trong "My Classes"
    • Lớp đã có sẵn học sinh
    • Teacher bắt đầu giao bài
```

**NOTE:** 1 Teacher có thể đảm nhiệm nhiều lớp (10A1, 10A2, 11B1)

### ✅ **Fix 6: Soft Delete Only**
```sql
-- Không xóa cứng, chỉ deactivate
UPDATE users 
SET is_active = false, 
    deactivated_at = NOW(),
    deactivated_by = <school_manager_id>
WHERE id = <user_id>;

-- Constraints:
-- Không thể deactivate Teacher đang có assignments active
-- Không thể deactivate Student đang có submissions pending
```

### ✅ **Fix 7: License Expired - Grace Mode**
```
KHI LICENSE HẾT HẠN:

┌────────────────────────────────────────┐
│  License Expired - Grace Period        │
├────────────────────────────────────────┤
│                                        │
│  🔒 Tài khoản của bạn bị hạn chế      │
│  vì license đã hết hạn.                │
│                                        │
│  [Gia hạn ngay] [Liên hệ Admin]       │
│                                        │
└────────────────────────────────────────┘

PERMISSIONS (Read-only mode):
  
SCHOOL:
  ✅ Login
  ✅ Xem data (reports, users)
  ✅ Export data
  ❌ Create/Edit/Delete users
  ❌ Tạo lớp mới
  → Show "Renew License" banner

STAFF:
  ✅ Login
  ✅ Xem bài cũ & điểm
  ❌ Tạo simulation mới
  ❌ Giao bài mới
  → Show "Renew License" banner

STUDENT:
  ✅ Login
  ✅ Xem điểm cũ
  ❌ Làm bài mới
  → Show "Your school's license has expired"
```

### ✅ **Fix 8: Only 1 SCHOOL**
```sql
-- Database constraint
CREATE UNIQUE INDEX idx_one_school_manager_per_school
ON users(school_id)
WHERE role = 'SCHOOL' AND is_active = true;

-- Nếu cần thay đổi SCHOOL:
-- 1. Deactivate current SCHOOL
-- 2. Promote another user to SCHOOL
```

### ✅ **Fix 9: Student in ONE Class at a Time**
```sql
CREATE TABLE class_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID REFERENCES classes(id) ON DELETE CASCADE,
    student_id UUID REFERENCES users(id) ON DELETE CASCADE,
    school_year VARCHAR(20) NOT NULL, -- "2025-2026"
    enrolled_at TIMESTAMP DEFAULT NOW(),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    
    -- ⭐ KEY CONSTRAINT: Một học sinh chỉ ở 1 lớp/năm học
    UNIQUE(student_id, school_year, status)
    -- status = 'ACTIVE' đảm bảo chỉ 1 enrollment active
);

-- Note: Học sinh có thể chuyển lớp (transfer):
-- 1. Set old enrollment status = 'TRANSFERRED'
-- 2. Create new enrollment với class mới
```

### ✅ **Fix 10: Role Naming**
```
OLD NAMES:
  SYSTEM_ADMIN  → confusing
  SCHOOL_ADMIN  → confusing

NEW NAMES:
  MANAGER          → Platform admin (clear)
  SCHOOL → School-level manager (clear)
```

---

## 💾 **DATABASE SCHEMA (FINAL)**

```sql
-- =====================================================
-- USERS TABLE với constraints đã fix
-- =====================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    avatar_url TEXT,
    
    -- Role
    role VARCHAR(20) NOT NULL,
    -- 'MANAGER' | 'REVIEWER' | 'SCHOOL' | 'STAFF' | 'STUDENT'
    
    -- School association
    school_id UUID REFERENCES schools(id) ON DELETE CASCADE,
    -- NULL for MANAGER and REVIEWER
    -- NOT NULL for SCHOOL, STAFF, STUDENT
    
    -- Soft delete
    is_active BOOLEAN DEFAULT true,
    deactivated_at TIMESTAMP,
    deactivated_by UUID REFERENCES users(id),
    deactivation_reason TEXT,
    
    -- Profile
    phone VARCHAR(20),
    date_of_birth DATE,
    gender VARCHAR(10),
    
    -- Student specific
    student_code VARCHAR(50),
    grade INTEGER, -- 10, 11, 12
    
    -- Teacher specific
    teacher_code VARCHAR(50),
    subject VARCHAR(100),
    
    -- Status
    is_verified BOOLEAN DEFAULT false,
    last_login TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- ⭐ CONSTRAINTS
    CONSTRAINT check_role_school_consistency 
        CHECK (
            (role IN ('MANAGER', 'REVIEWER') AND school_id IS NULL)
            OR
            (role IN ('SCHOOL', 'STAFF', 'STUDENT') AND school_id IS NOT NULL)
        )
);

-- ⭐ Only 1 active SCHOOL per school
CREATE UNIQUE INDEX idx_one_school_manager_per_school
ON users(school_id)
WHERE role = 'SCHOOL' AND is_active = true;

-- =====================================================
-- CLASSES & ENROLLMENTS với constraints đã fix
-- =====================================================

CREATE TABLE classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID REFERENCES schools(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL, -- "10A1"
    grade INTEGER NOT NULL, -- 10, 11, 12
    school_year VARCHAR(20) NOT NULL, -- "2025-2026"
    max_students INTEGER DEFAULT 45,
    description TEXT,
    
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by UUID REFERENCES users(id) -- SCHOOL
);

CREATE TABLE class_teachers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID REFERENCES classes(id) ON DELETE CASCADE,
    teacher_id UUID REFERENCES users(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT NOW(),
    assigned_by UUID REFERENCES users(id), -- SCHOOL
    
    UNIQUE(class_id, teacher_id)
);

CREATE TABLE class_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID REFERENCES classes(id) ON DELETE CASCADE,
    student_id UUID REFERENCES users(id) ON DELETE CASCADE,
    school_year VARCHAR(20) NOT NULL,
    enrolled_at TIMESTAMP DEFAULT NOW(),
    status VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, TRANSFERRED, GRADUATED
    
    -- ⭐ Student chỉ ở 1 lớp/năm học
    CONSTRAINT unique_student_per_year 
        UNIQUE(student_id, school_year) 
        WHERE status = 'ACTIVE'
);

-- =====================================================
-- SIMULATIONS với visibility logic
-- =====================================================

CREATE TABLE simulations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    specification JSONB NOT NULL,
    
    -- Ownership
    created_by UUID REFERENCES users(id),
    school_id UUID REFERENCES schools(id),
    -- NULL nếu created_by là MANAGER (official content)
    -- NOT NULL nếu created_by là STAFF
    
    -- Visibility
    visibility VARCHAR(20) DEFAULT 'PERSONAL',
    -- PERSONAL: Chỉ creator
    -- SHARED: Community (sau khi REVIEWER approve)
    
    -- Review status
    review_status VARCHAR(20) DEFAULT 'NOT_SUBMITTED',
    -- NOT_SUBMITTED, PENDING_REVIEW, APPROVED, REJECTED
    reviewed_by UUID REFERENCES users(id), -- REVIEWER
    reviewed_at TIMESTAMP,
    review_feedback TEXT,
    
    -- AI tracking
    ai_tokens_used INTEGER,
    
    -- Stats
    times_used INTEGER DEFAULT 0,
    avg_rating DECIMAL(3,2),
    
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_simulations_review ON simulations(review_status, created_at);
CREATE INDEX idx_simulations_visibility ON simulations(visibility, review_status);
```

---

## 🔄 **WORKFLOWS (FINAL)**

### **Workflow 1: School Onboarding**
```
[1] Trường đăng ký online
    ↓
[2] MANAGER verify & approve
    ↓
[3] MANAGER activate license
    ↓
[4] System TỰ ĐỘNG tạo 1 SCHOOL account
    • Email: email đăng ký
    • Password: random → send email
    ↓
[5] SCHOOL login lần đầu
    • Đổi password
    • Setup profile
    ↓
[6] SCHOOL import users (CSV)
    • Teachers: email, name, subject
    • Students: email, name, grade
    ↓
[7] SCHOOL tạo classes
    • 10A1: [Student1, Student2, ...]
    • 10A2: [Student3, Student4, ...]
    ↓
[8] SCHOOL phân giáo viên
    • Teacher X → 10A1, 10A2
    • Teacher Y → 11B1
    ↓
[9] Teachers & Students login → Start using
```

### **Workflow 2: Teacher Teaching**
```
[1] Teacher login → See "My Classes"
    • 10A1 (42 students)
    • 10A2 (38 students)
    ↓
[2] Teacher tạo simulation (AI)
    • Nhập đề bài
    • AI generate
    • Save to Personal Library
    ↓
[3] Teacher giao bài
    • Chọn class: 10A1
    • Chọn simulation
    • Set deadline, grading criteria
    • Assign
    ↓
[4] Students làm bài
    • Chạy simulation
    • Answer questions
    • Submit
    ↓
[5] AI auto-grade → PENDING_REVIEW
    ↓
[6] Teacher review & confirm grades
```

### **Workflow 3: Share to Community**
```
[1] Teacher có simulation tốt
    ↓
[2] Click "Share to Community"
    • Status: PENDING_REVIEW
    • Vào REVIEWER queue
    ↓
[3] REVIEWER review
    • Check physics accuracy
    • Check content quality
    • Test simulation
    ↓
[4a] APPROVE
     → visibility = 'SHARED'
     → All schools can use
     → Email notify teacher
     
[4b] REJECT
     → visibility = 'PERSONAL'
     → Send feedback to teacher
     → Teacher can fix & resubmit
```

---

## ✅ **IMPLEMENTATION CHECKLIST**

### **Backend:**
- [ ] Update `users` table with constraints
- [ ] Create UNIQUE index for SCHOOL
- [ ] Add soft delete columns
- [ ] Create `class_teachers` table
- [ ] Update `class_enrollments` with UNIQUE constraint
- [ ] Update `simulations` table with review_status
- [ ] Implement license expired check middleware
- [ ] API: Aggregate reports for SCHOOL

### **Frontend:**
- [ ] Rename roles in UI (MANAGER, SCHOOL)
- [ ] REVIEWER Console: Remove "Create" & "Assign" features
- [ ] SCHOOL Portal:
  - [ ] User management (with soft delete)
  - [ ] Class creation (with pre-assigned students)
  - [ ] Teacher assignment
  - [ ] Aggregate reports only
- [ ] License expired banner & read-only mode
- [ ] Teacher: "My Classes" shows multiple classes

### **Business Logic:**
- [ ] Prevent REVIEWER from creating simulations
- [ ] Prevent hard delete users
- [ ] Enforce 1 SCHOOL per school
- [ ] Enforce 1 student per class per year
- [ ] Grace period when license expires

---

## 📊 **SUMMARY TABLE**

| Role | Count/School | school_id | Can Create Sim | Can Assign | Can Review |
|------|--------------|-----------|----------------|------------|------------|
| MANAGER | N/A | NULL | ✅ | ✅ | ✅ |
| REVIEWER | N/A | NULL | ❌ | ❌ | ✅ |
| SCHOOL | **1** | UUID | ❌ | ❌ | ❌ |
| STAFF | 10-50 | UUID | ✅ | ✅ | ❌ |
| STUDENT | 500-5000 | UUID | ❌ | ❌ | ❌ |

---

**END OF DOCUMENT**

*This is the FINAL approved version ready for implementation.*
