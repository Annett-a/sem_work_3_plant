INSERT INTO users (email, password_hash, enabled)
VALUES ('admin@example.com',
        '$2a$10$CWImM/qDCmYRxJV2C1bR3.blNqQ2vJbEFFWMn79oY33rhE73IDke6',
        TRUE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         JOIN roles r ON r.name IN ('ROLE_USER', 'ROLE_ADMIN')
WHERE u.email = 'admin@example.com'
ON CONFLICT DO NOTHING;


-- email: admin@example.com
-- пароль: admin123