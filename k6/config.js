/**
 * k6 테스트 공통 설정
 * HH E-Commerce 부하 테스트용 설정 파일
 */

// 기본 설정
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트 사용자 수 범위
export const TEST_USER_COUNT = parseInt(__ENV.USER_COUNT) || 1000;

// 테스트 상품/쿠폰 ID
export const TEST_PRODUCT_IDS = [1, 2, 3, 4, 5];
export const TEST_PRODUCT_OPTION_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
export const TEST_COUPON_ID = parseInt(__ENV.COUPON_ID) || 1;

// HTTP 요청 기본 헤더
export const DEFAULT_HEADERS = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
};

// 성능 임계값 (thresholds)
export const DEFAULT_THRESHOLDS = {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>100'],
};

// 주문 테스트용 임계값
export const ORDER_THRESHOLDS = {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.05'],
};

// 쿠폰 테스트용 임계값 (실패율 높음 - 수량 소진)
export const COUPON_THRESHOLDS = {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    // 쿠폰 소진으로 인한 실패는 정상이므로 별도 체크
};

// 랜덤 사용자 ID 생성
export function getRandomUserId() {
    return Math.floor(Math.random() * TEST_USER_COUNT) + 1;
}

// 랜덤 상품 ID 생성
export function getRandomProductId() {
    return TEST_PRODUCT_IDS[Math.floor(Math.random() * TEST_PRODUCT_IDS.length)];
}

// 랜덤 상품 옵션 ID 생성
export function getRandomProductOptionId() {
    return TEST_PRODUCT_OPTION_IDS[Math.floor(Math.random() * TEST_PRODUCT_OPTION_IDS.length)];
}

// 랜덤 수량 생성 (1-3)
export function getRandomQuantity() {
    return Math.floor(Math.random() * 3) + 1;
}

// 응답 검증 헬퍼
export function checkResponse(response, expectedStatus = 200) {
    const checks = {
        'status is expected': response.status === expectedStatus,
        'response time < 2s': response.timings.duration < 2000,
    };

    if (expectedStatus === 200 && response.body) {
        try {
            JSON.parse(response.body);
            checks['valid JSON response'] = true;
        } catch (e) {
            checks['valid JSON response'] = false;
        }
    }

    return checks;
}
