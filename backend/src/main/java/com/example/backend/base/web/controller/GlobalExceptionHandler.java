package com.example.backend.base.web.controller;

import com.example.backend.base.web.dto.ErrorResponse;
import com.example.backend.exception.ApiException;
import com.example.backend.exception.CanonicalContractException;
import com.example.backend.exception.OutputContractException;
import com.example.backend.exception.PhysicsDomainException;
import com.example.backend.exception.SchemaCompilationException;
import com.example.backend.exception.SchemaRoutingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        ErrorResponse error = new ErrorResponse(ex.getStatus().value(), ex.getMessage(), ex.getCode(), ex.getStep());
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(CanonicalContractException.class)
    public ResponseEntity<ErrorResponse> handleCanonicalContract(CanonicalContractException ex) {
        log.warn("Invalid canonical contract", ex);
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse(422, "Dữ liệu mô phỏng không đúng định dạng. Vui lòng kiểm tra lại đầu vào."));
    }

    /** Invalid server-owned schema data is an internal configuration error. */
    @ExceptionHandler(SchemaCompilationException.class)
    public ResponseEntity<ErrorResponse> handleSchemaCompilation(SchemaCompilationException ex) {
        log.error("Invalid schema configuration", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Cấu hình mô hình không hợp lệ. Vui lòng liên hệ quản trị viên."));
    }

    /** Routing found no eligible candidate; the problem needs more information or human selection. */
    @ExceptionHandler(SchemaRoutingException.class)
    public ResponseEntity<ErrorResponse> handleSchemaRouting(SchemaRoutingException ex) {
        log.warn("Schema routing failed", ex);
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse(422, "Chưa tìm được mô hình phù hợp. Vui lòng bổ sung mô tả hoặc chọn lại chủ đề."));
    }

    /** A request quantity falls outside a model's physical domain. */
    @ExceptionHandler(PhysicsDomainException.class)
    public ResponseEntity<ErrorResponse> handlePhysicsDomain(PhysicsDomainException ex) {
        log.warn("Invalid physics input", ex);
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse(422, "Thông số nằm ngoài phạm vi tính toán của mô hình. Vui lòng kiểm tra lại giá trị và đơn vị."));
    }

    /** Solver-generated output violating its pinned contract is an internal solver defect. */
    @ExceptionHandler(OutputContractException.class)
    public ResponseEntity<ErrorResponse> handleOutputContract(OutputContractException ex) {
        log.error("Invalid solver output", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Kết quả tính toán mô phỏng không hợp lệ. Vui lòng thử lại sau."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Dữ liệu yêu cầu không hợp lệ");
        return ResponseEntity.badRequest().body(new ErrorResponse(400, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Nội dung yêu cầu không hợp lệ"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause() == null ? "" : ex.getMostSpecificCause().getMessage();
        String message = conflictMessage(detail);
        log.warn("Data integrity conflict: {}", detail);
        return ResponseEntity.status(409).body(new ErrorResponse(409, message));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticConflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, "Bản ghi vừa được người kiểm duyệt khác thay đổi; vui lòng tải lại."));
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
        ErrorResponse error = new ErrorResponse(500, "Máy chủ gặp lỗi. Vui lòng thử lại sau");
        return ResponseEntity.status(500).body(error);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(403).body(new ErrorResponse(403, "Bạn không có quyền thực hiện thao tác này"));
    }
}
