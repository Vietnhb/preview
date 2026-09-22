-- Store registration details on the pending bill. School and manager rows are
-- created atomically only after a signed successful VNPAY response.
ALTER TABLE school_payments ALTER COLUMN school_id DROP NOT NULL;
ALTER TABLE school_payments ALTER COLUMN manager_id DROP NOT NULL;

ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS registration_school_name VARCHAR(255);
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS registration_school_code VARCHAR(255);
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS registration_address VARCHAR(300);
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS registration_manager_name VARCHAR(255);
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS registration_email VARCHAR(255);
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS registration_phone_number VARCHAR(20);
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS registration_password_hash VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_registration_email
    ON school_payments (lower(registration_email))
    WHERE purpose = 'REGISTRATION' AND status = 'PENDING' AND registration_email IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_registration_school_code
    ON school_payments (upper(registration_school_code))
    WHERE purpose = 'REGISTRATION' AND status = 'PENDING' AND registration_school_code IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_registration_school_name
    ON school_payments (lower(registration_school_name))
    WHERE purpose = 'REGISTRATION' AND status = 'PENDING' AND registration_school_name IS NOT NULL;
