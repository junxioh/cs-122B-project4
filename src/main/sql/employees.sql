-- 创建employees表
CREATE TABLE IF NOT EXISTS employees (
    email VARCHAR(50) PRIMARY KEY,
    password VARCHAR(100) NOT NULL,  -- 增加长度以存储加密后的密码
    fullname VARCHAR(100) NOT NULL
);

-- 插入默认员工账号
-- 密码是 'classta' 使用BCrypt加密
INSERT INTO employees (email, password, fullname) 
VALUES ('classta@email.edu', '$2a$10$8K1p/a0dR1xqM1ZqKz8K6OQz8K1p/a0dR1xqM1ZqKz8K6OQz8K1p/a0d', 'CS122B TA')
ON DUPLICATE KEY UPDATE 
    password = '$2a$10$8K1p/a0dR1xqM1ZqKz8K6OQz8K1p/a0dR1xqM1ZqKz8K6OQz8K1p/a0d',
    fullname = 'CS122B TA'; 