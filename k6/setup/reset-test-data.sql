-- HH E-Commerce 부하 테스트용 데이터 리셋 스크립트
-- 테스트 반복 실행 전에 사용
-- 실행 방법: mysql -u ecommerce_user -p ecommerce < reset-test-data.sql

-- ================================================
-- 1. 주문 관련 데이터 초기화
-- ================================================
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM payment_coupon WHERE id > 0;
DELETE FROM payment WHERE id > 0;
DELETE FROM order_item WHERE id > 0;
DELETE FROM orders WHERE id > 0;
DELETE FROM cart WHERE id > 0;

SET FOREIGN_KEY_CHECKS = 1;

-- ================================================
-- 2. 쿠폰 발급 초기화
-- ================================================
DELETE FROM user_coupon WHERE id > 0;

-- 쿠폰 발급 수량 리셋
UPDATE coupon SET issued_quantity = 0 WHERE id > 0;

-- ================================================
-- 3. 포인트 리셋 (각 사용자당 1,000,000 포인트)
-- ================================================
UPDATE point SET balance = 1000000, version = 0 WHERE id > 0;

-- ================================================
-- 4. 상품 재고 리셋 (각 옵션당 1000개)
-- ================================================
UPDATE product_option SET stock = 1000 WHERE id > 0;

-- ================================================
-- 5. Redis 캐시 초기화 안내
-- ================================================
SELECT '=== 데이터 리셋 완료 ===' AS message;
SELECT '참고: Redis 캐시도 초기화하려면 다음 명령을 실행하세요:' AS note;
SELECT 'redis-cli FLUSHALL' AS redis_command;

-- ================================================
-- 6. 현재 상태 확인
-- ================================================
SELECT 'Current Status:' AS message;
SELECT
    (SELECT COUNT(*) FROM orders) AS order_count,
    (SELECT COUNT(*) FROM user_coupon) AS issued_coupon_count,
    (SELECT AVG(balance) FROM point) AS avg_point_balance,
    (SELECT AVG(stock) FROM product_option) AS avg_stock;
