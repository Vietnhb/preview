package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * License status DTO for frontend.
 * Tells UI whether to show renewal banner and block write operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LicenseStatusResponse {
    
    /**
     * Is license currently active (not expired).
     */
    private boolean isActive;
    
    /**
     * Is user in grace mode (expired license, read-only).
     */
    private boolean isInGraceMode;
    
    /**
     * Should show renewal banner (expired or < 30 days remaining).
     */
    private boolean showRenewalBanner;
    
    /**
     * Days until license expiry (negative if expired).
     */
    private long daysUntilExpiry;
    
    /**
     * Can user perform write operations (create simulations, assignments).
     */
    private boolean canPerformWriteOperations;
    
    /**
     * License end date (ISO format).
     */
    private String licenseEnd;
}
