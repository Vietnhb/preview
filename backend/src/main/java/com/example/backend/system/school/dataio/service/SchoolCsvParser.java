package com.example.backend.system.school.dataio.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.school.dataio.dto.SchoolImport.Row;
import com.example.backend.system.school.dataio.dto.SchoolImport;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded RFC 4180 reader; records may contain quoted commas, escaped quotes and newlines. */
public final class SchoolCsvParser {
    public static final int MAX_ROWS = 200;
    public static final int MAX_BYTES = 1_048_576;
    private SchoolCsvParser() { }

    public static List<Row> parse(byte[] bytes, List<String> columns, List<String> required) {
        if (bytes == null || bytes.length == 0) throw bad("Tệp CSV trống.");
        if (bytes.length > MAX_BYTES) throw bad("Tệp CSV tối đa 1 MB.");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) { throw bad("CSV phải dùng mã hóa UTF-8."); }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<List<String>> records = records(text);
        if (records.isEmpty()) throw bad("Tệp CSV không có dòng tiêu đề.");
        List<String> header = records.getFirst().stream().map(value -> canonical(value, columns)).toList();
        if (header.stream().distinct().count() != header.size()) throw bad("CSV có cột tiêu đề trùng nhau.");
        for (String name : header) if (!columns.contains(name)) throw bad("Cột không được hỗ trợ: " + name);
        for (String name : required) if (!header.contains(name)) throw bad("CSV thiếu cột " + name);
        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> values = records.get(index);
            if (values.stream().allMatch(String::isBlank)) continue;
            if (values.size() != header.size()) throw bad("Dòng " + (index + 1) + " có số cột khác tiêu đề.");
            Map<String, String> data = new LinkedHashMap<>();
            for (int column = 0; column < header.size(); column++) {
                String name = header.get(column);
                String value = values.get(column);
                data.put(name, name.equals("initialPassword") ? value : value.trim());
            }
            rows.add(new Row(index + 1, data));
        }
        if (rows.isEmpty()) throw bad("CSV chưa có dữ liệu.");
        return rows;
    }

    static List<List<String>> records(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '\0') throw bad("CSV chứa ký tự không hợp lệ.");
            if (quoted) {
                if (ch == '"' && index + 1 < text.length() && text.charAt(index + 1) == '"') { field.append('"'); index++; }
                else if (ch == '"') { quoted = false; closed = true; }
                else field.append(ch);
            } else if (ch == '"') {
                if (field.length() > 0 || closed) throw bad("Dấu ngoặc kép phải bao quanh toàn bộ ô CSV.");
                quoted = true;
            } else if (ch == ',' || ch == '\r' || ch == '\n') {
                fields.add(field.toString()); field.setLength(0); closed = false;
                if (ch != ',') {
                    records.add(fields); fields = new ArrayList<>();
                    if (ch == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                    if (records.size() > MAX_ROWS + 1) throw bad("Mỗi lần nhập tối đa " + MAX_ROWS + " dòng.");
                }
            } else {
                if (closed) throw bad("Không được có ký tự sau dấu ngoặc kép kết thúc ô.");
                field.append(ch);
            }
            if (field.length() > 10_000 || fields.size() > 20) throw bad("Ô hoặc số cột CSV vượt giới hạn.");
        }
        if (quoted) throw bad("CSV thiếu dấu ngoặc kép kết thúc ô.");
        if (!fields.isEmpty() || field.length() > 0 || closed) { fields.add(field.toString()); records.add(fields); }
        if (records.size() > MAX_ROWS + 1) throw bad("Mỗi lần nhập tối đa " + MAX_ROWS + " dòng.");
        return records;
    }

    private static String canonical(String value, List<String> columns) {
        String key = value.trim().replace("_", "").toLowerCase(Locale.ROOT);
        if (key.equals("password")) key = "initialpassword";
        for (String column : columns) if (column.toLowerCase(Locale.ROOT).equals(key)) return column;
        return value.trim();
    }
    private static ApiException bad(String message) { return ApiException.badRequest(message); }
}
