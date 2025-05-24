-- 创建员工表
CREATE TABLE IF NOT EXISTS employees (
    email VARCHAR(50) PRIMARY KEY,
    password VARCHAR(128) NOT NULL,  -- 使用更长的长度以存储加密密码
    fullname VARCHAR(100) NOT NULL
);

-- 创建存储过程：添加新电影
DELIMITER //
CREATE PROCEDURE add_movie(
    IN p_title VARCHAR(100),
    IN p_year INT,
    IN p_director VARCHAR(100),
    IN p_price DECIMAL(10,2),
    IN p_star_name VARCHAR(100),
    IN p_star_birth_year INT,
    IN p_genre_name VARCHAR(32)
)
BEGIN
    DECLARE v_movie_id VARCHAR(10);
    DECLARE v_star_id VARCHAR(10);
    DECLARE v_genre_id INT;
    DECLARE v_count INT;
    DECLARE v_message VARCHAR(500);
    DECLARE v_star_exists BOOLEAN;
    DECLARE v_genre_exists BOOLEAN;
    
    -- 检查电影是否已存在
    SELECT COUNT(*) INTO v_count FROM movies 
    WHERE title = p_title AND year = p_year AND director = p_director;
    
    IF v_count > 0 THEN
        SET v_message = CONCAT('Movie "', p_title, '" (', p_year, ') by ', p_director, ' already exists.');
    ELSE
        -- 生成新的电影ID
        SELECT CONCAT('tt', LPAD(COALESCE(MAX(SUBSTRING(id, 3)) + 1, 1), 7, '0'))
        INTO v_movie_id FROM movies WHERE id LIKE 'tt%';
        
        -- 插入新电影
        INSERT INTO movies (id, title, year, director, price)
        VALUES (v_movie_id, p_title, p_year, p_director, p_price);
        
        SET v_message = CONCAT('Movie "', p_title, '" (', p_year, ') by ', p_director, ' added successfully with ID: ', v_movie_id, '. ');
        
        -- 处理明星
        -- 检查明星是否存在
        SELECT COUNT(*) INTO v_count FROM stars WHERE name = p_star_name;
        SET v_star_exists = (v_count > 0);
        
        IF v_star_exists THEN
            -- 如果存在多个同名明星，选择第一个
            SELECT id INTO v_star_id FROM stars WHERE name = p_star_name LIMIT 1;
            SET v_message = CONCAT(v_message, 'Using existing star: ', p_star_name, ' (ID: ', v_star_id, '). ');
        ELSE
            -- 生成新的明星ID
            SELECT CONCAT('nm', LPAD(COALESCE(MAX(SUBSTRING(id, 3)) + 1, 1), 7, '0'))
            INTO v_star_id FROM stars WHERE id LIKE 'nm%';
            
            -- 插入新明星
            INSERT INTO stars (id, name, birthYear)
            VALUES (v_star_id, p_star_name, p_star_birth_year);
            
            SET v_message = CONCAT(v_message, 'Created new star: ', p_star_name, 
                IF(p_star_birth_year IS NULL, '', CONCAT(' (', p_star_birth_year, ')')), 
                ' with ID: ', v_star_id, '. ');
        END IF;
        
        -- 关联电影和明星
        INSERT INTO stars_in_movies (starId, movieId)
        VALUES (v_star_id, v_movie_id);
        
        -- 处理类型
        -- 检查类型是否存在
        SELECT COUNT(*) INTO v_count FROM genres WHERE name = p_genre_name;
        SET v_genre_exists = (v_count > 0);
        
        IF v_genre_exists THEN
            -- 使用现有类型
            SELECT id INTO v_genre_id FROM genres WHERE name = p_genre_name;
            SET v_message = CONCAT(v_message, 'Using existing genre: ', p_genre_name, ' (ID: ', v_genre_id, '). ');
        ELSE
            -- 获取新的类型ID
            SELECT COALESCE(MAX(id) + 1, 1) INTO v_genre_id FROM genres;
            
            -- 插入新类型
            INSERT INTO genres (id, name)
            VALUES (v_genre_id, p_genre_name);
            
            SET v_message = CONCAT(v_message, 'Created new genre: ', p_genre_name, ' with ID: ', v_genre_id, '. ');
        END IF;
        
        -- 关联电影和类型
        INSERT INTO genres_in_movies (genreId, movieId)
        VALUES (v_genre_id, v_movie_id);
    END IF;
    
    -- 返回状态消息
    SELECT v_message AS message;
END //
DELIMITER ;

-- 创建获取数据库元数据的存储过程
DELIMITER //
CREATE PROCEDURE get_database_metadata()
BEGIN
    SELECT 
        TABLE_NAME,
        COLUMN_NAME,
        COLUMN_TYPE,
        IS_NULLABLE,
        COLUMN_KEY,
        COLUMN_DEFAULT,
        EXTRA
    FROM 
        INFORMATION_SCHEMA.COLUMNS 
    WHERE 
        TABLE_SCHEMA = 'moviedb'
    ORDER BY 
        TABLE_NAME, ORDINAL_POSITION;
END //
DELIMITER ; 