package com.example.backend.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.backend.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        ErrorResponse error = new ErrorResponse(ex.getStatus().value(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(new ErrorResponse(400, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Request body is invalid"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause() == null ? "" : ex.getMostSpecificCause().getMessage();
        String message = conflictMessage(detail);
        log.warn("Data integrity conflict: {}", detail);
        return ResponseEntity.status(409).body(new ErrorResponse(409, message));
    }

    private String conflictMessage(String detail) {
        String normalized = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("users_email_key")) return "Email đã tồn tại";
        if (normalized.contains("schools_code_key")) return "Mã trường đã tồn tại";
        if (normalized.contains("schools_name_key")) return "Tên trường đã tồn tại";
        if (normalized.contains("idx_one_school_manager_per_school"))
            return "Trường này đã có quản lý trường đang hoạt động";
        if (normalized.contains("idx_active_enrollment_per_year"))
            return "Học sinh đã thuộc một lớp đang hoạt động trong năm học này";
        if (normalized.contains("unique_active_enrollment_per_year"))
            return "Học sinh đã thuộc một lớp trong năm học này";
        if (normalized.contains("check_role_school_consistency") || normalized.contains("user role and school"))
            return "Vai trò và trường của tài khoản không hợp lệ";
        return "Dữ liệu đã tồn tại hoặc đang được sử dụng";
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error for {} {}", request.getMethod(), request.getRequestURI(), ex);
        ErrorResponse error = new ErrorResponse(500, "Internal server error");
        return ResponseEntity.status(500).body(error);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(403).body(new ErrorResponse(403, "You do not have permission for this action"));
    }
}
