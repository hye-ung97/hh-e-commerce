-- HH E-Commerce 부하 테스트용 데이터 초기화 스크립트
-- 실행 방법: mysql -u ecommerce_user -p ecommerce < init-test-data.sql

-- ================================================
-- 1. 기존 테스트 데이터 정리
-- ================================================
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM payment_coupon WHERE id > 0;
DELETE FROM payment WHERE id > 0;
DELETE FROM order_item WHERE id > 0;
DELETE FROM orders WHERE id > 0;
DELETE FROM cart WHERE id > 0;
DELETE FROM user_coupon WHERE id > 0;
DELETE FROM coupon WHERE id > 0;
DELETE FROM product_option WHERE id > 0;
DELETE FROM product WHERE id > 0;
DELETE FROM point WHERE id > 0;
DELETE FROM user WHERE id > 0;

SET FOREIGN_KEY_CHECKS = 1;

-- ================================================
-- 2. 테스트 사용자 생성 (10,000명)
-- ================================================
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS create_test_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 10000 DO
        INSERT INTO user (id, name, email, created_at, updated_at)
        VALUES (i, CONCAT('TestUser', i), CONCAT('user', i, '@test.com'), NOW(), NOW());
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

CALL create_test_users();
DROP PROCEDURE IF EXISTS create_test_users;

-- ================================================
-- 3. 테스트 포인트 생성 (각 사용자당 1,000,000 포인트)
-- ================================================
INSERT INTO point (user_id, balance, version, created_at, updated_at)
SELECT id, 1000000, 0, NOW(), NOW()
FROM user;

-- ================================================
-- 4. 테스트 상품 생성 (100개)
-- ================================================
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS create_test_products()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 100 DO
        INSERT INTO product (id, name, description, base_price, status, created_at, updated_at)
        VALUES (
            i,
            CONCAT('테스트 상품 ', i),
            CONCAT('테스트 상품 ', i, '의 설명입니다.'),
            10000 + (i * 100),
            'ACTIVE',
            NOW(),
            NOW()
        );
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

CALL create_test_products();
DROP PROCEDURE IF EXISTS create_test_products;

-- ================================================
-- 5. 테스트 상품 옵션 생성 (상품당 3개씩, 총 300개)
-- ================================================
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS create_test_product_options()
BEGIN
    DECLARE prod_id INT DEFAULT 1;
    DECLARE opt_num INT;
    DECLARE opt_id INT DEFAULT 1;

    WHILE prod_id <= 100 DO
        SET opt_num = 1;
        WHILE opt_num <= 3 DO
            INSERT INTO product_option (id, product_id, name, additional_price, stock, created_at, updated_at)
            VALUES (
                opt_id,
                prod_id,
                CONCAT('옵션 ', opt_num),
                opt_num * 500,
                1000,  -- 각 옵션당 재고 1000개
                NOW(),
                NOW()
            );
            SET opt_id = opt_id + 1;
            SET opt_num = opt_num + 1;
        END WHILE;
        SET prod_id = prod_id + 1;
    END WHILE;
END //
DELIMITER ;

CALL create_test_product_options();
DROP PROCEDURE IF EXISTS create_test_product_options;

-- ================================================
-- 6. 테스트 쿠폰 생성 (5개)
-- ================================================
INSERT INTO coupon (id, name, discount_type, discount_value, min_order_amount, max_discount_amount, total_quantity, issued_quantity, valid_from, valid_until, created_at, updated_at)
VALUES
-- 쿠폰 1: 선착순 100개 (10% 할인)
(1, '선착순 10% 할인 쿠폰', 'PERCENTAGE', 10, 10000, 5000, 100, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW()),

-- 쿠폰 2: 선착순 50개 (1000원 할인)
(2, '선착순 1000원 할인 쿠폰', 'FIXED', 1000, 5000, 1000, 50, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW()),

-- 쿠폰 3: 선착순 200개 (5% 할인)
(3, '선착순 5% 할인 쿠폰', 'PERCENTAGE', 5, 5000, 3000, 200, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW()),

-- 쿠폰 4: 선착순 1000개 (500원 할인)
(4, '대량 500원 할인 쿠폰', 'FIXED', 500, 3000, 500, 1000, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW()),

-- 쿠폰 5: 선착순 10개 (VIP 20% 할인)
(5, 'VIP 20% 할인 쿠폰', 'PERCENTAGE', 20, 50000, 20000, 10, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW());

-- ================================================
-- 7. 데이터 확인
-- ================================================
SELECT '=== 테스트 데이터 초기화 완료 ===' AS message;
SELECT COUNT(*) AS user_count FROM user;
SELECT COUNT(*) AS point_count FROM point;
SELECT COUNT(*) AS product_count FROM product;
SELECT COUNT(*) AS product_option_count FROM product_option;
SELECT COUNT(*) AS coupon_count FROM coupon;

SELECT 'Coupon Details:' AS message;
SELECT id, name, total_quantity, issued_quantity FROM coupon;
