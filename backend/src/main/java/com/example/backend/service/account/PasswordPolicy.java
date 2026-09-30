package com.example.backend.service.account;

import com.example.backend.exception.ApiException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/** Reject inputs BCrypt cannot encode in full instead of truncating or returning an encoder error. */
public final class PasswordPolicy {
    private PasswordPolicy() { }

    public static void requireValid(String password) {
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 120)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu phải có từ 8 đến 120 ký tự.");
        requireEncodable(password);
    }

    public static void requireEncodable(String password) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu vượt quá giới hạn 72 byte UTF-8. Vui lòng dùng mật khẩu ngắn hơn.");
    }
}
