package com.example.backend.system.school.dataio.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.school.dataio.dto.SchoolImport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchoolCsvParserTest {
    private static List<SchoolImport.Row> parse(String csv) {
        return SchoolCsvParser.parse(csv.getBytes(StandardCharsets.UTF_8), List.of("fullName", "email", "initialPassword"), List.of("fullName", "email"));
    }
    @Test void readsBomQuotedUnicodeCommasEscapedQuotesAndMultilineCells() {
        var rows = parse("\uFEFFfull_name,email,password\r\n\"Nguyễn, \"\"An\"\"\nMinh\",an@example.edu.vn,12345678\r\n");
        assertEquals(1, rows.size());
        assertEquals("Nguyễn, \"An\"\nMinh", rows.getFirst().data().get("fullName"));
        assertEquals("12345678", rows.getFirst().data().get("initialPassword"));
    }
    @Test void rejectsMalformedQuotesAndMismatchedColumns() {
        assertThrows(ApiException.class, () -> parse("fullName,email\n\"An,an@example.edu.vn"));
        assertThrows(ApiException.class, () -> parse("fullName,email\nAn,an@example.edu.vn,extra"));
        assertThrows(ApiException.class, () -> parse("fullName,email\n\"An\"junk,an@example.edu.vn"));
    }
    @Test void preservesSuppliedPasswordWhitespaceWhileTrimmingProfileFields() {
        var rows = parse("fullName,email,password\n\"  Nguyễn An  \",\" an@example.edu.vn \",\"  Abcd1234!  \"\n");
        assertEquals("Nguyễn An", rows.getFirst().data().get("fullName"));
        assertEquals("an@example.edu.vn", rows.getFirst().data().get("email"));
        assertEquals("  Abcd1234!  ", rows.getFirst().data().get("initialPassword"));
    }
    @Test void rejectsDuplicateUnknownHeadersAndInvalidUtf8() {
        assertThrows(ApiException.class, () -> parse("fullName,FULL_NAME,email\nAn,An,a@example.edu.vn"));
        assertThrows(ApiException.class, () -> parse("fullName,email,schoolId\nAn,a@example.edu.vn,external-id"));
        assertThrows(ApiException.class, () -> SchoolCsvParser.parse(new byte[]{(byte) 0xc3, (byte) 0x28}, List.of("email"), List.of("email")));
    }
    @Test void enforcesRowByteAndCellLimitsBeforeProducingRows() {
        String rows = "fullName,email\n" + "An,a@example.edu.vn\n".repeat(201);
        assertThrows(ApiException.class, () -> parse(rows));
        assertThrows(ApiException.class, () -> parse("fullName,email\n" + "A".repeat(10_001) + ",a@example.edu.vn"));
        assertThrows(ApiException.class, () -> SchoolCsvParser.parse(new byte[SchoolCsvParser.MAX_BYTES + 1], List.of("email"), List.of("email")));
        assertEquals(200, parse("fullName,email\n" + "An,a@example.edu.vn\n".repeat(200)).size());
    }
}
