# B2B Role System - Implementation Summary

**Status**: B2B account/license, school class management, reporting, payment reconciliation, assignment grading and shared-library moderation are implemented in code. External Supabase/VNPAY credentials and production QA remain environment-dependent.

## Integration update — 19/09/2026

Confirmed product decisions:
- AI quota uses actual provider `usage.total_tokens`, not simulation counts.
- Let an in-flight call finish and record all tokens; block the next AI call once quota is exhausted.
- Public personal signup is disabled. A school can self-register a `SCHOOL_MANAGER`, choose a plan and activate after verified VNPAY payment; managers create TEACHER/STUDENT accounts in their own school.

Implemented in this increment:
- Restored compatibility of the existing school/admin APIs with the new school relationship.
- Added school-scoped account creation/update/listing and `/school`, reusing the existing account UI.
- Added license dates/token allowance to school administration and `/api/user/me/license` with a renewal/read-only banner.
- Expired/unconfigured/future licenses block school API writes; disabled schools invalidate existing JWT access. Profile maintenance remains available.
- Unified REVIEWER authorization, blocked reviewer teaching creation, and fixed student assignment routing.
- AI calls record actual usage in an independent transaction, serialize quota checks per school, and reset the usage bucket at the calendar month boundary (server date). Retries with a provider response are charged individually; local rule-based work is not charged.
- New token columns avoid interpreting the old simulation counters as tokens. Schools with no configured license stay read-only.
- Corrected PostgreSQL role enforcement, manager uniqueness, legacy school mapping, and the partial active-enrollment index. SQL initialization uses `;;` so function bodies remain intact.
- Added school-scoped class management at `/api/schools/{schoolId}/classes` and `/school/classes`: create/update/archive classes, assign teachers, enroll/remove students, transfer students within an academic year, and enforce one active class per student/year.
- Assignment creation now verifies that the teacher and students share a school, each student has an active enrollment, and the teacher is assigned to that student's class.
- Added Supabase SQL for `school_classes`, `class_teacher_assignments` and `class_enrollments`, including active-enrollment and class-name constraints.
- Added assignment grading criteria (`maxScore`, expected numeric value and tolerance), optional auto-grade, teacher confirmation/feedback/reopen, pending-submission blocking and assignment aggregate reports.
- Added student action-log storage for assignment open/prediction actions and exposes score, feedback, grading status and retry state in student assignment data.
- Added Shared Library moderation status/audit fields, reviewer queue actions and a teacher clone endpoint for approved shared simulations.
- Added school CSV import with row-level results, school/class/token reports and class CSV export.
- Added token usage audit rows per AI call, admin payment reconciliation scheduler/UI/revenue summary, and real admin Feedback/Messages data routes with response modal.

Validation:
- Backend unit tests and frontend production build pass; B2B policy and HTTP authorization regression tests added.
- Deployment target is Supabase PostgreSQL. The application already supports Supabase transaction pooler (`6543`) and direct/session connections (`5432`); run `data.sql` once through Supabase SQL Editor or the direct connection, then deploy with `JPA_DDL_AUTO=validate` and `SQL_INIT_MODE=never`. A live Supabase migration was not executed from this workspace.
- No live provider request was made. Missing/invalid provider usage returns an error rather than an invented token count. Network failures without usage cannot yet be reconciled against provider billing.

Still outstanding (not claimed complete): solver-specific multi-step grading formulas, realtime action-log streaming, VNPAY refund/auto-renew/email integrations, production migration and live provider/browser QA. Legacy users without a known school need explicit administrator assignment; no school is guessed.

The sections below describe the earlier implementation snapshot; this update takes precedence where behavior differs.


---

## 🐛 Bug Fixed: REVIEWER Permissions

### Issue Discovered
**File**: `AdminService.java` and role-aware teaching services - `canCreateSimulation()` policy

**Problem**: REVIEWER was incorrectly allowed to create simulations
```java
// BEFORE (WRONG):
public boolean canCreateSimulation(User user) {
    if (user.getSchool() == null) {
        return true; // ❌ Allowed ALL platform users (ADMIN + REVIEWER)
    }
    // ...
}
```

**Root Cause**: Logic assumed `school_id = NULL` → can create, but REVIEWER should NOT create teaching simulations.

### Design Clarification
After reviewing proposal (`PhysLive_Full_Scope_Requirements.md`), confirmed:

**REVIEWER = Curriculum Expert** (not QA reviewer):
- ✅ **Creates** topic schema (FR-REV-01) - defines knowledge structure (Kinematics, Dynamics, etc.)
- ✅ **Creates** reference solver (FR-REV-02) - standard algorithm for each topic
- ✅ **Creates** benchmark problems (FR-REV-06) - evaluation problems for AI solver
- ✅ **Reviews** Shared Library - approves teacher-submitted simulations (FR-REV-04)
- ✅ **Handles** ambiguous extraction (FR-REV-03) - resolves NLP ambiguity
- ❌ **Does NOT create** teaching simulations - that's TEACHER's job
- ❌ **Does NOT assign** homework or grade - teaching operations only for TEACHER

**Key Distinction**:
- **REVIEWER** = Curriculum designer (creates schemas/solvers for AI)
- **TEACHER** = Content creator (creates simulations using schemas)

### Fix Applied
```java
// AFTER (CORRECT):
public boolean canCreateSimulation(User user) {
    String roleName = user.getRole().getName();
    
    // Platform roles
    if ("ADMIN".equals(roleName)) return true;
    if ("REVIEWER".equals(roleName)) return false; // ✅ Fixed
    
    // School roles
    if ("TEACHER".equals(roleName)) {
        return licenseCheckService.canPerformWriteOperations(user);
    }
    
    return false;
}
```

**Same fix applied to**:
- `canCreateAssignment()` - REVIEWER cannot assign homework
- All other teaching-related permissions

### UI Verification
Checked existing UI components:

**✅ UI is CORRECT** (per proposal FR-REV):
- `ReviewerConsole.tsx` → VersionsTab has "Tạo bản nháp mới" (Create draft) - ✅ Creates **schema**, not simulation
- `ReviewerConsole.tsx` → BenchmarksTab has "Thêm bài Benchmark" - ✅ Creates **benchmark**, not simulation
- No "Create Simulation" button for REVIEWER - ✅ Correct

**Documentation was wrong, not UI** - now aligned.

---

## 📋 What Was Implemented

### 1. Database Schema & Migrations ✅
**Files**: `backend/src/main/resources/data.sql`

- ✅ 5 roles: `ADMIN`, `REVIEWER`, `SCHOOL_MANAGER`, `TEACHER`, `STUDENT`
  - **REVIEWER** = Curriculum Expert (creates schema/solver/benchmark, reviews shared library)
  - **Not** a QA reviewer - responsible for curriculum design per FR-REV-01/02/06
- ✅ CHECK constraint: Platform roles (ADMIN, REVIEWER) → `school_id` = NULL
- ✅ CHECK constraint: School roles → `school_id` NOT NULL  
- ✅ UNIQUE index: 1 SCHOOL_MANAGER per school
- ✅ Auto-migration on startup (idempotent SQL)

### 2. JPA Entities ✅
**New entities created**:

| Entity | Purpose | Key Constraints |
|--------|---------|----------------|
| `School` | B2B customer | License dates, AI quota tracking |
| `SchoolClass` | Class (Lớp 10A1) | Belongs to school + school year |
| `ClassEnrollment` | Student → Class | UNIQUE: 1 student/1 class/year (status=ACTIVE) |
| `ClassTeacherAssignment` | Teacher → Class | 1 teacher can teach multiple classes |

**Updated entity**:
- `User`: Added `School` FK, soft delete fields (`deactivatedBy`, `deactivationReason`, `deactivatedAt`)

### 3. Repositories ✅
All repositories created with custom queries:
- `SchoolRepository`: Find expired licenses, quota exceeded
- `SchoolClassRepository`: By school + year
- `ClassEnrollmentRepository`: Active enrollments, count students
- `ClassTeacherAssignmentRepository`: Teacher's classes
- `UserRepository`: By school + role, quota checks

### 4. Business Logic Services ✅

#### `SchoolService`
- CRUD schools (ADMIN only)
- Quota increment/tracking
- License validation

#### `LicenseCheckService` 
- ✅ Grace mode: Expired license → read-only access
- ✅ Renewal banner: < 30 days warning
- ✅ Write operations blocked when expired

#### `AdminService` (school-scoped account management)
- ✅ Soft delete: `setActive(userId, false/true)`
- ✅ Tracks: who, when, why
- ✅ Combines license + quota checks

#### `RoleValidationService`
- ✅ Enforces platform vs school role rules
- ✅ Validates 1 SCHOOL_MANAGER per school

### 5. Spring Security RBAC ✅
**File**: `SecurityConfig.java`

Role-based URL patterns configured:
```java
/api/admin/**           → ADMIN only
/api/reviewer/**        → REVIEWER, ADMIN
/api/schools/*/users    → SCHOOL_MANAGER, ADMIN
/api/simulations/create → TEACHER, ADMIN
/api/assignments/*/submit → STUDENT, ADMIN
```

Method-level security: `@PreAuthorize("hasRole('...')")` on all services

### 6. Frontend Types ✅
**Files**: `react-client/src/types/`

- ✅ `roles.ts`: RoleType enum + helper functions
- ✅ `auth.ts`, `user.ts`: Updated with RoleType + schoolId
- ✅ `school.ts`: School, Class, Enrollment types
- ✅ `index.ts`: Barrel export

The current frontend includes role-aware admin, school, teacher, reviewer and student screens; this section records the original type-layer snapshot only.

---

## 🎯 Business Rules Enforced

### Role-School Consistency
✅ Platform roles (`ADMIN`, `REVIEWER`) → `school_id` = NULL  
✅ School roles (`SCHOOL_MANAGER`, `TEACHER`, `STUDENT`) → `school_id` NOT NULL

### School Manager Constraint
✅ Only 1 `SCHOOL_MANAGER` per school (UNIQUE index)

### Student Enrollment
✅ 1 student in 1 class per school year (UNIQUE constraint)  
✅ Transfer allowed: old → `TRANSFERRED`, new → `ACTIVE`

### License Expiry (Grace Mode)
✅ Expired license: Users can login (read-only)  
✅ Blocked: Create simulations, assignments, submissions  
✅ Allowed: View content, reports  
✅ Banner: Shows when < 30 days or expired

### Soft Delete
✅ No hard delete - `active` = false  
✅ Tracks: `deactivatedBy`, `deactivationReason`, `deactivatedAt`  
✅ Login check: Deactivated users blocked

### AI Quota
✅ Monthly quota per school  
✅ Increment on simulation generation  
✅ Block creation when quota exceeded

---

## 📦 Files Created/Modified

### Backend (20 files)
**Entities** (5 new + 1 updated):
- `School.java`
- `SchoolClass.java`
- `ClassEnrollment.java`
- `ClassTeacherAssignment.java`
- `User.java` (updated)

**Repositories** (4 new + 1 updated):
- `SchoolRepository.java`
- `SchoolClassRepository.java`
- `ClassEnrollmentRepository.java`
- `ClassTeacherAssignmentRepository.java`
- `UserRepository.java` (updated)

**Services** (4 new + 1 updated):
- `SchoolService.java`
- `LicenseCheckService.java`
- `AdminService.java`
- `RoleValidationService.java`
- `AuthService.java` (updated)

**Security** (2 updated):
- `SecurityConfig.java`
- `RoleConstants.java` (new)

**DTO** (1 new):
- `LicenseStatusResponse.java`

**Database**:
- `data.sql` (updated with roles + constraints)

### Frontend (5 files)
**Types**:
- `roles.ts` (new)
- `school.ts` (new)
- `index.ts` (new barrel export)
- `auth.ts` (updated)
- `user.ts` (updated)

---

## ✅ Verification Checklist

### Database
- [ ] Run Spring Boot → check `data.sql` executes
- [ ] Verify 5 roles in `roles` table
- [ ] Verify constraints: `check_role_school_consistency`
- [ ] Verify UNIQUE index: `idx_one_school_manager_per_school`

### Business Logic
- [ ] Create school → success (ADMIN)
- [ ] Create school → fail (TEACHER)
- [ ] Assign 2nd SCHOOL_MANAGER → fail (UNIQUE constraint)
- [ ] Enroll student in 2 classes same year → fail (UNIQUE constraint)
- [ ] Login with expired license → success (grace mode)
- [ ] Create simulation with expired license → fail
- [ ] Deactivate user → login fails
- [ ] Reactivate user → login succeeds

### Security
- [ ] `/api/admin/**` → 401 for non-ADMIN
- [ ] `/api/reviewer/**` → accessible by REVIEWER
- [ ] `/api/schools/{id}/users` → accessible by SCHOOL_MANAGER (own school)
- [ ] `/api/simulations/create` → accessible by TEACHER

---

## 🚀 Next Steps (Not Done)

### Immediate (Required for Production)
1. **Controllers**: Create REST endpoints for services
2. **DTOs**: Request/Response objects for APIs
3. **Testing**: Unit + integration tests
4. **Error handling**: Global exception handler
5. **Validation**: Input validation with `@Valid`

### Phase 2 (After Backend Done)
1. **UI Components**: School Manager dashboard
2. **License Banner**: Frontend component
3. **Role Guards**: React route protection
4. **API Integration**: Connect services to UI

### Phase 3 (Enhancement)
1. **Audit Log**: Track all admin actions
2. **Bulk Operations**: Import users, create classes
3. **Reports**: School analytics, usage metrics
4. **Notifications**: Email on license expiry

---

## 📖 Usage Examples

### Check License Status
```java
@Autowired
private LicenseCheckService licenseCheckService;

boolean canWrite = licenseCheckService.canPerformWriteOperations(currentUser);
if (!canWrite) {
    throw new ApiException(HttpStatus.FORBIDDEN, "License expired - read-only mode");
}
```

### Deactivate User (Soft Delete)
```java
@Autowired
private AdminService adminService;

adminService.setActive(userId, false);
```

### Validate Role-School Consistency
```java
@Autowired
private RoleValidationService roleValidation;

roleValidation.validateRoleSchoolConsistency(roleName, school);
roleValidation.validateSingleSchoolManager(schoolId, null);
```

### Increment AI Quota
```java
@Autowired
private SchoolService schoolService;

boolean success = schoolService.incrementQuotaUsage(school.getId());
if (!success) {
    throw new ApiException(HttpStatus.FORBIDDEN, "Monthly quota exceeded");
}
```

---

## 🏗️ Architecture Decisions

1. **No Flyway/Liquibase**: Used JPA `ddl-auto=update` + idempotent SQL
2. **String-based roles**: Stored in DB, not enum (flexibility)
3. **Soft delete only**: No hard delete (data retention)
4. **Grace mode on expiry**: Allow login, block writes
5. **Simple services**: No over-engineering, straightforward logic
6. **Minimal frontend**: Types only, UI when needed

---

**Completed**: All 8 backend tasks  
**Ready for**: Controller implementation + Testing  
**Not included**: UI components (intentional - build separately)

---

## 📚 References

### Proposal Documents
- **Full Requirements**: `D:\FPT_FALL_2026\SEP490\proposal\PhysLive_Full_Scope_Requirements.md`
- **Original Proposal**: `D:\FPT_FALL_2026\SEP490\proposal\FA26SE309.docx`

### REVIEWER Requirements (from proposal)
- **FR-REV-01**: Create/manage topic schema (knowledge structure definition)
- **FR-REV-02**: Create/manage reference solver (standard algorithm per topic)
- **FR-REV-03**: Handle ambiguous extraction (NLP disambiguation)
- **FR-REV-04**: Review shared library (approve teacher submissions)
- **FR-REV-06**: Create benchmark problems (AI solver evaluation)

### Key Design Documents
- **ROLES_FINAL.md**: Complete role permission matrix
- **B2B_SYSTEM_DESIGN.md**: System architecture (if exists)
- **Backend entities**: `backend/src/main/java/com/example/backend/entity/`
- **Frontend types**: `react-client/src/types/`
