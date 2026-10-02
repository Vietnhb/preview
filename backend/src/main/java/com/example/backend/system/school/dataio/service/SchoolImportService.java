package com.example.backend.system.school.dataio.service;

import com.example.backend.exception.ApiException;
import com.example.backend.security.PasswordPolicy;
import com.example.backend.system.account.dto.CreateManagedUserRequest;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.model.enums.PermissionCode;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.repository.UserRepository;
import com.example.backend.system.account.service.AccountAccessService;
import com.example.backend.system.account.service.AdminService;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.account.service.RoleValidationService;
import com.example.backend.system.school.dataio.dto.SchoolImport;
import com.example.backend.system.school.dto.SchoolClassContracts.SchoolClassRequest;
import com.example.backend.system.school.dto.SchoolClassContracts;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.school.model.entity.SchoolClass;
import com.example.backend.system.school.repository.ClassEnrollmentRepository;
import com.example.backend.system.school.repository.ClassTeacherAssignmentRepository;
import com.example.backend.system.school.repository.SchoolClassRepository;
import com.example.backend.system.school.repository.SchoolRepository;
import com.example.backend.system.school.service.LicenseCheckService;
import com.example.backend.system.school.service.SchoolClassService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Preview is read-only; commit revalidates the complete bounded batch in one transaction. */
@Service
@RequiredArgsConstructor
public class SchoolImportService {
    private final SchoolRepository schools;
    private final SchoolClassRepository classes;
    private final ClassEnrollmentRepository enrollments;
    private final ClassTeacherAssignmentRepository assignments;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final RoleValidationService roles;
    private final AccountAccessService accountAccess;
    private final LicenseCheckService license;
    private final AdminService admin;
    private final SchoolClassService classService;
    private final EntityManager entityManager;
    private final byte[] previewSecret = randomBytes(32);

    private static final List<String> STAFF_PERMISSIONS = PermissionCode.forRole(RoleName.STAFF).stream().map(Enum::name).toList();

    /** A cell holds one or more permission codes separated by "|" (";", "+" and spaces are also accepted). */
    private static List<String> permissionCodes(String cell) {
        if (cell == null || cell.isBlank()) return null;
        List<String> codes = java.util.Arrays.stream(cell.toUpperCase(Locale.ROOT).split("[|;+,\\s]+")).filter(code -> !code.isBlank()).distinct().toList();
        return codes.isEmpty() ? null : codes;
    }

    public static List<String> columns(SchoolImport.Kind kind) {
        if (kind == null) throw bad("Chọn loại dữ liệu cần nhập.");
        return switch (kind) {
            case USERS -> List.of("fullName", "dateOfBirth", "email", "initialPassword", "role", "permissions", "classCode", "schoolYear", "avatarUrl");
            case CLASSES -> List.of("classCode", "gradeLevel", "schoolYear", "subject");
            case ENROLLMENTS -> List.of("studentEmail", "classCode", "schoolYear");
            case TEACHER_ASSIGNMENTS -> List.of("teacherEmail", "classCode", "schoolYear");
        };
    }

    private static List<String> required(SchoolImport.Kind kind) {
        return switch (kind) {
            case USERS -> List.of("fullName", "dateOfBirth", "email", "initialPassword", "role");
            case CLASSES -> List.of("classCode", "gradeLevel", "schoolYear");
            case ENROLLMENTS -> List.of("studentEmail", "classCode", "schoolYear");
            case TEACHER_ASSIGNMENTS -> List.of("teacherEmail", "classCode", "schoolYear");
        };
    }

    @Transactional(readOnly = true)
    public byte[] template(UUID schoolId, SchoolImport.Kind kind) {
        requireAccess(schoolId, kind);
        int year = LocalDate.now().getMonthValue() >= 8 ? LocalDate.now().getYear() : LocalDate.now().getYear() - 1;
        String schoolYear = year + "-" + (year + 1);
        List<String> example = switch (kind) {
            case USERS -> List.of("Nguyễn Minh An", "2010-09-15", "minhan@example.edu.vn", "ChangeMe2026!", "STUDENT", "", "", "", "");
            case CLASSES -> List.of("10A1", "10", schoolYear, "Vật lý");
            case ENROLLMENTS -> List.of("minhan@example.edu.vn", "10A1", schoolYear);
            case TEACHER_ASSIGNMENTS -> List.of("giaovien@example.edu.vn", "10A1", schoolYear);
        };
        String teacherExample = kind == SchoolImport.Kind.USERS ? csv(List.of("Trần Thu Hà", "1988-04-23", "thuha@example.edu.vn", "ChangeMe2026!", "STAFF", "TEACH", "", "", "")) + "\r\n" : "";
        return ("\uFEFF" + csv(columns(kind)) + "\r\n" + csv(example) + "\r\n" + teacherExample).getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public SchoolImport.Preview previewFile(UUID schoolId, SchoolImport.Kind kind, byte[] csvBytes) {
        requireAccess(schoolId, kind);
        if (csvBytes == null || csvBytes.length == 0) throw bad("Chọn tệp CSV.");
        if (csvBytes.length > SchoolCsvParser.MAX_BYTES) throw bad("Tệp CSV tối đa 1 MB.");
        return preview(schoolId, new SchoolImport.Request(kind, SchoolCsvParser.parse(csvBytes, columns(kind), required(kind))));
    }

    @Transactional(readOnly = true)
    public SchoolImport.Preview preview(UUID schoolId, SchoolImport.Request request) {
        checkRequest(request);
        User actor = requireAccess(schoolId, request.kind());
        School school = schools.findById(schoolId).orElseThrow(() -> ApiException.notFound("Không tìm thấy trường."));
        requireActiveSchool(school);
        return validate(schoolId, actor, school, request);
    }

    @Transactional
    public SchoolImport.Result commit(UUID schoolId, SchoolImport.Request request) {
        checkRequest(request);
        User actor = requireAccess(schoolId, request.kind());
        School school = schools.findByIdForUpdate(schoolId).orElseThrow(() -> ApiException.notFound("Không tìm thấy trường."));
        entityManager.refresh(school, LockModeType.PESSIMISTIC_WRITE);
        requireActiveSchool(school);
        if (!hasRole(actor, RoleName.MANAGER)) license.requireWriteAccess(actor);
        requirePreview(schoolId, actor, request);
        lockReferences(schoolId, request);
        SchoolImport.Preview preview = validate(schoolId, actor, school, request);
        if (!preview.canCommit()) throw bad("Dữ liệu đã thay đổi hoặc còn lỗi. Hãy kiểm tra lại bản xem trước.");
        List<SchoolImport.Credential> credentials = new ArrayList<>();
        for (SchoolImport.PreviewRow row : preview.rows()) {
            Map<String, String> data = row.data();
            switch (request.kind()) {
                case USERS -> {
                    var created = admin.createUser(new CreateManagedUserRequest(data.get("email"), data.get("initialPassword"),
                            data.get("fullName"), data.get("role"), schoolId.toString(), LocalDate.parse(data.get("dateOfBirth")),
                            blankToNull(data.get("avatarUrl")), permissionCodes(data.get("permissions"))));
                    if (!data.get("classCode").isBlank()) {
                        SchoolClass target = findClass(schoolId, data.get("classCode"), data.get("schoolYear"));
                        if (RoleName.STUDENT.matches(data.get("role"))) classService.enrollStudent(schoolId, target.getId(), created.id());
                        else classService.assignTeacher(schoolId, target.getId(), created.id());
                    }
                    credentials.add(new SchoolImport.Credential(data.get("fullName"), LocalDate.parse(data.get("dateOfBirth")),
                            data.get("email"), data.get("initialPassword"), data.get("role"), data.get("classCode"), data.get("schoolYear")));
                }
                case CLASSES -> classService.create(schoolId, new SchoolClassRequest(data.get("classCode"), Integer.valueOf(data.get("gradeLevel")), data.get("schoolYear"), data.get("subject")));
                case ENROLLMENTS -> classService.enrollStudent(schoolId, findClass(schoolId, data.get("classCode"), data.get("schoolYear")).getId(), users.findFirstByEmailIgnoreCase(data.get("studentEmail")).orElseThrow().getId());
                case TEACHER_ASSIGNMENTS -> classService.assignTeacher(schoolId, findClass(schoolId, data.get("classCode"), data.get("schoolYear")).getId(), users.findFirstByEmailIgnoreCase(data.get("teacherEmail")).orElseThrow().getId());
            }
        }
        return new SchoolImport.Result(request.kind(), preview.rows().size(), List.copyOf(credentials));
    }

    private SchoolImport.Preview validate(UUID schoolId, User actor, School school, SchoolImport.Request request) {
        List<SchoolClass> schoolClasses = classes.findBySchoolIdAndIsActiveTrue(schoolId);
        List<User> schoolUsers = request.kind() == SchoolImport.Kind.USERS ? users.findBySchoolId(schoolId) : List.of();
        Map<String, Long> emailCounts = new HashMap<>(), identityCounts = new HashMap<>(), classCounts = new HashMap<>();
        List<SchoolImport.Row> normalized = request.rows().stream().map(row -> normalize(request.kind(), row)).toList();
        for (SchoolImport.Row row : normalized) {
            var data = row.data();
            String emailKey = switch (request.kind()) {
                case USERS -> data.get("email");
                case ENROLLMENTS -> data.get("studentEmail") + "|" + data.get("schoolYear");
                case TEACHER_ASSIGNMENTS -> data.get("teacherEmail") + "|" + data.get("classCode").toLowerCase(Locale.ROOT) + "|" + data.get("schoolYear");
                case CLASSES -> "";
            };
            emailCounts.merge(emailKey, 1L, Long::sum);
            identityCounts.merge(identity(data.get("fullName"), data.get("dateOfBirth")), 1L, Long::sum);
            classCounts.merge(classKey(data.get("classCode"), data.get("schoolYear")), 1L, Long::sum);
        }
        long studentCount = normalized.stream().filter(row -> "STUDENT".equals(row.data().get("role"))).count();
        boolean userQuotaExceeded = request.kind() == SchoolImport.Kind.USERS && school.getStudentQuota() != null
                && users.countActiveStudents(schoolId) + studentCount > school.getStudentQuota();
        long incomingEnrollments = request.kind() == SchoolImport.Kind.ENROLLMENTS ? normalized.size()
                : request.kind() == SchoolImport.Kind.USERS ? normalized.stream().filter(row -> "STUDENT".equals(row.data().get("role")) && !row.data().get("classCode").isBlank()).count() : 0;
        boolean enrollmentQuotaExceeded = incomingEnrollments > 0 && school.getStudentQuota() != null
                && enrollments.countActiveStudentsBySchoolId(schoolId) + incomingEnrollments > school.getStudentQuota();
        List<SchoolImport.PreviewRow> result = new ArrayList<>();
        for (SchoolImport.Row row : normalized) {
            Map<String, String> data = row.data();
            List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();
            List<SchoolImport.Match> matches = new ArrayList<>();
            if (request.kind() == SchoolImport.Kind.USERS) {
                requiredText(data.get("fullName"), 120, "Họ tên", errors);
                LocalDate dob = date(data.get("dateOfBirth"), errors);
                validEmail(data.get("email"), errors);
                if (emailCounts.get(data.get("email")) > 1) errors.add("Email trùng trong tệp.");
                if (users.findFirstByEmailIgnoreCase(data.get("email")).isPresent()) errors.add("Email đã được sử dụng.");
                if (!Set.of("STAFF", "STUDENT").contains(data.get("role"))) errors.add("Vai trò phải là STAFF hoặc STUDENT.");
                try { PasswordPolicy.requireValid(data.get("initialPassword")); }
                catch (ApiException ex) { errors.add("Mật khẩu ban đầu cần ít nhất 8 ký tự và tối đa 72 byte UTF-8."); }
                if (RoleName.STAFF.matches(data.get("role"))) {
                    List<String> codes = permissionCodes(data.get("permissions"));
                    if (codes == null || !STAFF_PERMISSIONS.containsAll(codes)) errors.add("permissions phải là TEACH, DEPARTMENT_HEAD_PHYSICS hoặc TEACH|DEPARTMENT_HEAD_PHYSICS.");
                } else if (!data.get("permissions").isBlank()) errors.add("permissions chỉ áp dụng cho STAFF.");
                if (!data.get("avatarUrl").isBlank() && (data.get("avatarUrl").length() > 2000 || !data.get("avatarUrl").matches("https?://[^\\s]+"))) errors.add("Ảnh đại diện cần URL http hoặc https hợp lệ.");
                if (dob != null) for (User existing : schoolUsers) {
                    if (dob.equals(existing.getDateOfBirth()) && identity(data.get("fullName"), "").equals(identity(existing.getFullName(), "")))
                        matches.add(new SchoolImport.Match(existing.getId(), existing.getEmail(), existing.getFullName(), existing.getDateOfBirth()));
                }
                if (!matches.isEmpty()) warnings.add("Trùng họ tên và ngày sinh với tài khoản trong trường; vẫn tạo tài khoản mới khi email khác.");
                if (identityCounts.get(identity(data.get("fullName"), data.get("dateOfBirth"))) > 1) warnings.add("Có dòng khác trong tệp cùng họ tên và ngày sinh.");
                if (userQuotaExceeded && RoleName.STUDENT.matches(data.get("role"))) errors.add("Số học sinh mới vượt hạn mức của trường.");
                if (data.get("classCode").isBlank() != data.get("schoolYear").isBlank()) errors.add("Nhập cả classCode và schoolYear để xếp lớp hoặc phân công.");
            }
            if (request.kind() == SchoolImport.Kind.CLASSES) {
                requiredText(data.get("classCode"), 100, "Mã lớp", errors);
                validYear(data.get("schoolYear"), errors);
                if (!Set.of("10", "11", "12").contains(data.get("gradeLevel"))) errors.add("Khối phải là 10, 11 hoặc 12.");
                if (data.get("subject").length() > 50) errors.add("Môn học tối đa 50 ký tự.");
                if (classCounts.get(classKey(data.get("classCode"), data.get("schoolYear"))) > 1) errors.add("Lớp và năm học trùng trong tệp.");
                if (classes.existsBySchoolIdAndNameIgnoreCaseAndSchoolYear(schoolId, data.get("classCode"), data.get("schoolYear"))) errors.add("Lớp đã tồn tại trong năm học này.");
            } else if (!data.get("classCode").isBlank() || request.kind() != SchoolImport.Kind.USERS) {
                requiredText(data.get("classCode"), 100, "Mã lớp", errors);
                validYear(data.get("schoolYear"), errors);
                SchoolClass target = schoolClasses.stream().filter(item -> Boolean.TRUE.equals(item.getIsActive()) && item.getName().equalsIgnoreCase(data.get("classCode")) && item.getSchoolYear().equals(data.get("schoolYear"))).findFirst().orElse(null);
                if (target == null) errors.add("Không tìm thấy lớp đang hoạt động trong trường và năm học này.");
                if (request.kind() == SchoolImport.Kind.ENROLLMENTS || request.kind() == SchoolImport.Kind.TEACHER_ASSIGNMENTS) {
                    boolean student = request.kind() == SchoolImport.Kind.ENROLLMENTS;
                    String email = data.get(student ? "studentEmail" : "teacherEmail");
                    validEmail(email, errors);
                    User person = users.findFirstByEmailIgnoreCase(email).orElse(null);
                    if (person == null || person.getSchool() == null || !schoolId.equals(person.getSchool().getId())
                            || Boolean.FALSE.equals(person.getActive()) || !hasRole(person, student ? RoleName.STUDENT : RoleName.STAFF)) errors.add(student ? "Học sinh không hợp lệ trong trường này." : "Giáo viên không hợp lệ trong trường này.");
                    else if (target != null) {
                        if (student && enrollments.findActiveEnrollment(person.getId(), data.get("schoolYear")).isPresent()) errors.add("Học sinh đã được xếp lớp trong năm học này. Dùng chức năng chuyển lớp nếu cần.");
                        if (!student && assignments.existsBySchoolClassIdAndTeacherIdAndIsActiveTrue(target.getId(), person.getId())) errors.add("Giáo viên đã được phân công cho lớp này.");
                    }
                    String key = student ? email + "|" + data.get("schoolYear") : email + "|" + data.get("classCode").toLowerCase(Locale.ROOT) + "|" + data.get("schoolYear");
                    if (emailCounts.get(key) > 1) errors.add(student ? "Học sinh có nhiều dòng trong cùng năm học." : "Phân công trùng trong tệp.");
                }
            }
            if (enrollmentQuotaExceeded && (request.kind() == SchoolImport.Kind.ENROLLMENTS || "STUDENT".equals(data.get("role")) && !data.get("classCode").isBlank())) errors.add("Số lượt xếp lớp vượt hạn mức của trường.");
            result.add(new SchoolImport.PreviewRow(row.row(), data, List.copyOf(errors), List.copyOf(warnings), List.copyOf(matches)));
        }
        int invalid = (int) result.stream().filter(row -> !row.errors().isEmpty()).count();
        String token = invalid == 0 ? signPreview(schoolId, actor, new SchoolImport.Request(request.kind(), normalized), Instant.now().getEpochSecond()) : null;
        return new SchoolImport.Preview(request.kind(), columns(request.kind()), List.copyOf(result), result.size() - invalid, invalid, invalid == 0, token);
    }

    private User requireAccess(UUID schoolId, SchoolImport.Kind kind) {
        columns(kind);
        User actor = currentUser.requireCurrentUser();
        boolean head = accountAccess.isDepartmentHead(actor) && actor.getSchool() != null && schoolId.equals(actor.getSchool().getId());
        if (!roles.canManageSchool(actor, schoolId) && !head) throw ApiException.forbidden("Không có quyền quản lý dữ liệu trường này.");
        if (kind == SchoolImport.Kind.CLASSES && !hasRole(actor, RoleName.SCHOOL)) throw ApiException.forbidden("Chỉ SCHOOL được tạo lớp học.");
        if (kind == SchoolImport.Kind.USERS && head) throw ApiException.forbidden("Trưởng bộ môn chỉ được xếp lớp và phân công giáo viên.");
        return actor;
    }

    private static void checkRequest(SchoolImport.Request request) {
        if (request == null || request.kind() == null || request.rows() == null || request.rows().isEmpty()) throw bad("Dữ liệu nhập chưa có dòng nào.");
        if (request.rows().size() > SchoolCsvParser.MAX_ROWS) throw bad("Mỗi lần nhập tối đa 200 dòng.");
        Set<Integer> rowNumbers = new HashSet<>();
        long bytes = 0;
        for (SchoolImport.Row row : request.rows()) {
            if (row == null || row.row() < 1 || !rowNumbers.add(row.row()) || row.data() == null) throw bad("Số dòng hoặc dữ liệu dòng không hợp lệ.");
            for (Map.Entry<String, String> entry : row.data().entrySet()) {
                if (!columns(request.kind()).contains(entry.getKey()) || entry.getValue() == null || entry.getValue().length() > 10_000 || entry.getValue().indexOf('\0') >= 0) throw bad("Cột hoặc ô dữ liệu không hợp lệ.");
                bytes += entry.getValue().getBytes(StandardCharsets.UTF_8).length;
            }
        }
        if (bytes > SchoolCsvParser.MAX_BYTES) throw bad("Dữ liệu nhập tối đa 1 MB.");
    }

    private static SchoolImport.Row normalize(SchoolImport.Kind kind, SchoolImport.Row row) {
        Map<String, String> data = new LinkedHashMap<>();
        for (String column : columns(kind)) {
            String value = row.data().getOrDefault(column, "");
            data.put(column, column.equals("initialPassword") ? value : value.trim());
        }
        for (String column : List.of("email", "studentEmail", "teacherEmail")) if (data.containsKey(column)) data.put(column, data.get(column).toLowerCase(Locale.ROOT));
        if (kind == SchoolImport.Kind.USERS) {
            data.put("role", data.get("role").toUpperCase(Locale.ROOT));
            List<String> codes = permissionCodes(data.get("permissions"));
            data.put("permissions", codes == null ? "" : String.join("|", codes));
            if (RoleName.STAFF.matches(data.get("role")) && codes == null) data.put("permissions", PermissionCode.TEACH.name());
            if (data.get("initialPassword").isBlank()) data.put("initialPassword", "Pl!" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(12)));
        }
        return new SchoolImport.Row(row.row(), data);
    }

    private SchoolClass findClass(UUID schoolId, String code, String year) {
        return classes.findBySchoolIdAndIsActiveTrue(schoolId).stream().filter(item -> Boolean.TRUE.equals(item.getIsActive()) && item.getName().equalsIgnoreCase(code) && item.getSchoolYear().equals(year)).findFirst()
                .orElseThrow(() -> bad("Lớp đã thay đổi. Hãy kiểm tra lại dữ liệu."));
    }
    private void lockReferences(UUID schoolId, SchoolImport.Request request) {
        if (request.kind() == SchoolImport.Kind.CLASSES) return;
        Set<String> classKeys = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (SchoolImport.Row row : request.rows()) {
            Map<String, String> data = row.data();
            if (!data.getOrDefault("classCode", "").isBlank()) classKeys.add(classKey(data.get("classCode"), data.get("schoolYear")));
            if (request.kind() == SchoolImport.Kind.ENROLLMENTS) emails.add(data.get("studentEmail"));
            if (request.kind() == SchoolImport.Kind.TEACHER_ASSIGNMENTS) emails.add(data.get("teacherEmail"));
        }
        if (!classKeys.isEmpty()) classes.findBySchoolIdAndIsActiveTrue(schoolId).stream().filter(item -> classKeys.contains(classKey(item.getName(), item.getSchoolYear())))
                .sorted(java.util.Comparator.comparing(SchoolClass::getId)).forEach(item -> entityManager.refresh(item, LockModeType.PESSIMISTIC_WRITE));
        emails.stream().map(users::findFirstByEmailIgnoreCase).flatMap(java.util.Optional::stream)
                .filter(item -> item.getSchool() != null && schoolId.equals(item.getSchool().getId()))
                .sorted(java.util.Comparator.comparing(User::getId)).forEach(item -> entityManager.refresh(item, LockModeType.PESSIMISTIC_WRITE));
    }
    private static void requireActiveSchool(School school) { if (!school.isActive()) throw ApiException.forbidden("Trường đã bị tạm khóa."); }
    private static boolean hasRole(User user, RoleName role) { return user != null && user.getRole() != null && role.matches(user.getRole().getName()); }
    private static String identity(String name, String dob) { return Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT) + "|" + (dob == null ? "" : dob); }
    private static String classKey(String code, String year) { return (code == null ? "" : code.toLowerCase(Locale.ROOT)) + "|" + year; }
    private static void requiredText(String value, int max, String label, List<String> errors) { if (value.isBlank() || value.length() > max) errors.add(label + " cần 1–" + max + " ký tự."); }
    private static void validEmail(String value, List<String> errors) { if (value.length() > 254 || !value.matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")) errors.add("Email không hợp lệ."); }
    private static LocalDate date(String value, List<String> errors) {
        try {
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("format", value, 0);
            LocalDate date = LocalDate.parse(value);
            if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.of(1900, 1, 1))) throw new DateTimeParseException("range", value, 0);
            return date;
        } catch (DateTimeParseException ex) { errors.add("Ngày sinh cần yyyy-MM-dd và không được ở tương lai."); return null; }
    }
    private static void validYear(String value, List<String> errors) {
        if (!value.matches("\\d{4}-\\d{4}") || Integer.parseInt(value.substring(5)) != Integer.parseInt(value.substring(0, 4)) + 1) errors.add("Năm học cần dạng 2026-2027 với hai năm liên tiếp.");
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static String csv(List<String> fields) { return String.join(",", fields.stream().map(value -> "\"" + value.replace("\"", "\"\"") + "\"").toList()); }
    private static byte[] randomBytes(int size) { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return bytes; }

    // A short-lived signature binds the reviewed rows to this school and actor without storing passwords server-side.
    private String signPreview(UUID schoolId, User actor, SchoolImport.Request request, long issued) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(previewSecret, "HmacSHA256"));
            mac.update((schoolId + "|" + actor.getId() + "|" + request.kind() + "|" + issued).getBytes(StandardCharsets.UTF_8));
            for (SchoolImport.Row row : request.rows()) {
                mac.update(("|" + row.row()).getBytes(StandardCharsets.UTF_8));
                for (String column : columns(request.kind())) {
                    byte[] value = row.data().getOrDefault(column, "").getBytes(StandardCharsets.UTF_8);
                    mac.update(("|" + value.length + ":").getBytes(StandardCharsets.UTF_8)); mac.update(value);
                }
            }
            return issued + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("Cannot sign import preview", ex); }
    }
    private void requirePreview(UUID schoolId, User actor, SchoolImport.Request request) {
        try {
            String token = request.previewToken();
            if (token == null || token.length() > 100) throw new IllegalArgumentException();
            long issued = Long.parseLong(token.split("\\.", 2)[0]);
            long age = Instant.now().getEpochSecond() - issued;
            if (age < 0 || age > 900 || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), signPreview(schoolId, actor, request, issued).getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) { throw bad("Hãy kiểm tra dữ liệu trước khi nhập. Bản xem trước có hiệu lực 15 phút."); }
    }
    private static ApiException bad(String message) { return ApiException.badRequest(message); }
}
