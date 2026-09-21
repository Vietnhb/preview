# ROLES & PERMISSIONS - PHYSLIVE B2B (FINAL VERSION)

> Confirmed 19/09/2026: school accounts are provisioned by managers (no public signup). AI quota is actual provider tokens; finish and charge an in-flight call fully, then block subsequent calls when exhausted. See B2B_IMPLEMENTATION_SUMMARY.md for implemented scope and verification limits.


**Date:** 17/09/2026  
**Status:** ✅ APPROVED - Ready for Implementation

---

## 🎯 **5 ROLES - FINAL**

```
┌─────────────────────────────────────────────────────────┐
│              PLATFORM LEVEL                             │
│                                                         │
│  1. ADMIN               (Quản trị viên platform)        │
│     • school_id = NULL                                  │
│     • Quản lý toàn platform                             │
│                                                         │
│  2. REVIEWER    (Chuyên gia nội dung & Kiểm duyệt)  │
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
│  3. SCHOOL_MANAGER      (Quản lý trường)                │
│     • school_id = <UUID>                                │
│     • CHỈ 1 người/trường                                │
│     • Quản lý users & classes                           │
│     • Xem báo cáo TỔNG HỢP (không chi tiết HS)         │
│     • Phân giáo viên vào lớp                            │
│     • Lớp có SẴN học sinh tương ứng                    │
│                                                         │
│  4. TEACHER             (Giáo viên)                     │
│     • school_id = <UUID>                                │
│     • 1 GV đảm nhiệm NHIỀU LỚP                         │
│     • SCHOOL_MANAGER cho phép vào lớp                  │
│     • Tạo simulations & giao bài                        │
│                                                         │
│  5. STUDENT             (Học sinh)                      │
│     • school_id = <UUID>                                │
│     • MỖI LẦN chỉ ở 1 LỚP                              │
│     • Làm bài & xem điểm                                │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 📊 **PERMISSION MATRIX (CORRECTED)**

| Tính năng | ADMIN | REVIEWER | SCHOOL_MGR | TEACHER | STUDENT |
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

### ✅ **Fix 3: SCHOOL_MANAGER - Aggregate Reports Only**
```
SCHOOL_MANAGER xem:
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

[1] SCHOOL_MANAGER tạo lớp
    • Tên lớp: 10A1
    • Khối: 10
    • Danh sách học sinh: [HS1, HS2, HS3, ...]
    ↓
[2] SCHOOL_MANAGER phân giáo viên
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
  
SCHOOL_MANAGER:
  ✅ Login
  ✅ Xem data (reports, users)
  ✅ Export data
  ❌ Create/Edit/Delete users
  ❌ Tạo lớp mới
  → Show "Renew License" banner

TEACHER:
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

### ✅ **Fix 8: Only 1 SCHOOL_MANAGER**
```sql
-- Database constraint
CREATE UNIQUE INDEX idx_one_school_manager_per_school
ON users(school_id)
WHERE role = 'SCHOOL_MANAGER' AND is_active = true;

-- Nếu cần thay đổi SCHOOL_MANAGER:
-- 1. Deactivate current SCHOOL_MANAGER
-- 2. Promote another user to SCHOOL_MANAGER
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
  ADMIN          → Platform admin (clear)
  SCHOOL_MANAGER → School-level manager (clear)
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
    -- 'ADMIN' | 'REVIEWER' | 'SCHOOL_MANAGER' | 'TEACHER' | 'STUDENT'
    
    -- School association
    school_id UUID REFERENCES schools(id) ON DELETE CASCADE,
    -- NULL for ADMIN and REVIEWER
    -- NOT NULL for SCHOOL_MANAGER, TEACHER, STUDENT
    
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
            (role IN ('ADMIN', 'REVIEWER') AND school_id IS NULL)
            OR
            (role IN ('SCHOOL_MANAGER', 'TEACHER', 'STUDENT') AND school_id IS NOT NULL)
        )
);

-- ⭐ Only 1 active SCHOOL_MANAGER per school
CREATE UNIQUE INDEX idx_one_school_manager_per_school
ON users(school_id)
WHERE role = 'SCHOOL_MANAGER' AND is_active = true;

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
    created_by UUID REFERENCES users(id) -- SCHOOL_MANAGER
);

CREATE TABLE class_teachers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID REFERENCES classes(id) ON DELETE CASCADE,
    teacher_id UUID REFERENCES users(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT NOW(),
    assigned_by UUID REFERENCES users(id), -- SCHOOL_MANAGER
    
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
    -- NULL nếu created_by là ADMIN (official content)
    -- NOT NULL nếu created_by là TEACHER
    
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
[2] ADMIN verify & approve
    ↓
[3] ADMIN activate license
    ↓
[4] System TỰ ĐỘNG tạo 1 SCHOOL_MANAGER account
    • Email: email đăng ký
    • Password: random → send email
    ↓
[5] SCHOOL_MANAGER login lần đầu
    • Đổi password
    • Setup profile
    ↓
[6] SCHOOL_MANAGER import users (CSV)
    • Teachers: email, name, subject
    • Students: email, name, grade
    ↓
[7] SCHOOL_MANAGER tạo classes
    • 10A1: [Student1, Student2, ...]
    • 10A2: [Student3, Student4, ...]
    ↓
[8] SCHOOL_MANAGER phân giáo viên
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
- [ ] Create UNIQUE index for SCHOOL_MANAGER
- [ ] Add soft delete columns
- [ ] Create `class_teachers` table
- [ ] Update `class_enrollments` with UNIQUE constraint
- [ ] Update `simulations` table with review_status
- [ ] Implement license expired check middleware
- [ ] API: Aggregate reports for SCHOOL_MANAGER

### **Frontend:**
- [ ] Rename roles in UI (ADMIN, SCHOOL_MANAGER)
- [ ] REVIEWER Console: Remove "Create" & "Assign" features
- [ ] SCHOOL_MANAGER Portal:
  - [ ] User management (with soft delete)
  - [ ] Class creation (with pre-assigned students)
  - [ ] Teacher assignment
  - [ ] Aggregate reports only
- [ ] License expired banner & read-only mode
- [ ] Teacher: "My Classes" shows multiple classes

### **Business Logic:**
- [ ] Prevent REVIEWER from creating simulations
- [ ] Prevent hard delete users
- [ ] Enforce 1 SCHOOL_MANAGER per school
- [ ] Enforce 1 student per class per year
- [ ] Grace period when license expires

---

## 📊 **SUMMARY TABLE**

| Role | Count/School | school_id | Can Create Sim | Can Assign | Can Review |
|------|--------------|-----------|----------------|------------|------------|
| ADMIN | N/A | NULL | ✅ | ✅ | ✅ |
| REVIEWER | N/A | NULL | ❌ | ❌ | ✅ |
| SCHOOL_MANAGER | **1** | UUID | ❌ | ❌ | ❌ |
| TEACHER | 10-50 | UUID | ✅ | ✅ | ❌ |
| STUDENT | 500-5000 | UUID | ❌ | ❌ | ❌ |

---

**END OF DOCUMENT**

*This is the FINAL approved version ready for implementation.*
