CREATE UNIQUE INDEX uk_users_single_admin
    ON users (role)
    WHERE role = 'ADMIN';
