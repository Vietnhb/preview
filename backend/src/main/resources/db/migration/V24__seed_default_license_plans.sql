-- Publish the initial catalog when the database is created through Flyway.
-- Keep administrator changes intact on existing installations.
INSERT INTO license_plans (code, name, description, annual_price_vnd, student_quota, monthly_token_quota, active)
VALUES
    ('STARTER', 'Starter', 'Dành cho trường bắt đầu triển khai lớp học mô phỏng.', 30000000, 500, 100000, TRUE),
    ('PROFESSIONAL', 'Professional', 'Dành cho trường triển khai trên nhiều khối lớp.', 50000000, 1000, 300000, TRUE),
    ('ENTERPRISE', 'Enterprise', 'Dành cho trường quy mô lớn và nhu cầu AI cao.', 200000000, 5000, NULL, TRUE)
ON CONFLICT (code) DO NOTHING;
