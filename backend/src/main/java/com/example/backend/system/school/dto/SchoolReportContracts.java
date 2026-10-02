package com.example.backend.system.school.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Requests and responses for school operations. */
public final class SchoolReportContracts {
    private SchoolReportContracts() { }

    public record Summary(UUID schoolId, String schoolName, long students, long teachers, long managers,
                          long activeClasses, long enrolledStudents, long usedTokens, Integer tokenQuota,
                          LocalDate licenseEnd) { }
    public record ClassRow(UUID id, String name, Integer gradeLevel, String schoolYear,
                           long teachers, long students) { }
    public record TokenRow(String userEmail, String operation, long tokens, LocalDate usageMonth, java.time.Instant recordedAt) { }
}
