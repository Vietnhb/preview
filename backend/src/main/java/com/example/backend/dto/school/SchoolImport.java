package com.example.backend.dto.school;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** One contract for school CSV preview, edited rows and atomic commit. */
public final class SchoolImport {
    private SchoolImport() { }
    public enum Kind { USERS, CLASSES, ENROLLMENTS, TEACHER_ASSIGNMENTS }
    public record Row(int row, Map<String, String> data) { }
    public record Request(Kind kind, List<Row> rows, String previewToken) {
        public Request(Kind kind, List<Row> rows) { this(kind, rows, null); }
    }
    public record Match(Integer id, String email, String fullName, LocalDate dateOfBirth) { }
    public record PreviewRow(int row, Map<String, String> data,
                             List<String> errors, List<String> warnings, List<Match> matches) { }
    public record Preview(Kind kind, List<String> columns, List<PreviewRow> rows,
                          int validRows, int invalidRows, boolean canCommit, String previewToken) { }
    public record Credential(String fullName, LocalDate dateOfBirth, String email, String initialPassword,
                             String role, String classCode, String schoolYear) { }
    public record Result(Kind kind, int imported, List<Credential> credentials) { }
}
