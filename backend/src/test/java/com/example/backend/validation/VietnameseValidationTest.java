package com.example.backend.validation;

import jakarta.validation.Validation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VietnameseValidationTest {
    record Input(@NotBlank String title, @DecimalMin(value = "1", inclusive = false) double value) {}

    @Test
    void validationMessagesStayVietnameseForEnglishClients() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var interpolator = factory.getMessageInterpolator();
            var validator = factory.usingContext().messageInterpolator(new jakarta.validation.MessageInterpolator() {
                public String interpolate(String template, Context context) {
                    return interpolator.interpolate(template, context, Locale.ENGLISH);
                }
                public String interpolate(String template, Context context, Locale locale) {
                    return interpolator.interpolate(template, context, Locale.ENGLISH);
                }
            }).getValidator();
            var violations = validator.validate(new Input("", 1));
            var messages = violations.stream().collect(java.util.stream.Collectors.toMap(
                    violation -> violation.getPropertyPath().toString(), violation -> violation.getMessage()));
            assertEquals("Không được để trống", messages.get("title"));
            assertEquals("Giá trị phải lớn hơn 1", messages.get("value"));
        }
    }
}
