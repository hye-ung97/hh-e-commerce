/**
 * 시나리오 3: 혼합 워크로드 테스트 (Mixed Workload Test)
 *
 * 목적: 실제 서비스와 유사한 트래픽 패턴에서의 시스템 안정성 검증
 *
 * 트래픽 비율:
 * - 읽기 (70%): 상품 목록 40%, 상품 상세 20%, 인기 상품 10%
 * - 쓰기 (25%): 장바구니 추가 15%, 주문 생성 10%
 * - 이벤트 (5%): 쿠폰 발급 5%
 *
 * 실행 방법:
 * k6 run scenarios/03-mixed-workload-test.js
 *
 * 환경변수:
 * - BASE_URL: API 서버 URL (기본: http://localhost:8080)
 * - VUS: 동시 사용자 수 (기본: 200)
 * - DURATION: 테스트 지속 시간 (기본: 10m)
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import {
    BASE_URL,
    DEFAULT_HEADERS,
    getRandomUserId,
    getRandomProductId,
    getRandomProductOptionId,
    getRandomQuantity
} from '../config.js';

// 커스텀 메트릭 - API별
const productListDuration = new Trend('product_list_duration');
const productDetailDuration = new Trend('product_detail_duration');
const popularProductDuration = new Trend('popular_product_duration');
const cartAddDuration = new Trend('cart_add_duration');
const orderCreateDuration = new Trend('order_create_duration');
const couponIssueDuration = new Trend('coupon_issue_duration');

const apiSuccessRate = new Rate('api_success_rate');
const readSuccessRate = new Rate('read_success_rate');
const writeSuccessRate = new Rate('write_success_rate');

// 요청 카운터
const requestCounter = new Counter('total_requests');

// 테스트 설정
export const options = {
    scenarios: {
        mixed_workload: {
            executor: 'constant-vus',
            vus: parseInt(__ENV.VUS) || 100,
            duration: __ENV.DURATION || '10m',
        },
    },
    thresholds: {
        'product_list_duration': ['p(95)<300'],
        'product_detail_duration': ['p(95)<200'],
        'popular_product_duration': ['p(95)<150'],
        'cart_add_duration': ['p(95)<500'],
        'order_create_duration': ['p(95)<2000'],
        'api_success_rate': ['rate>0.95'],
        'read_success_rate': ['rate>0.99'],
        'http_req_failed': ['rate<0.05'],
    },
};

// 워크로드 비율 정의
const WORKLOAD_WEIGHTS = {
    productList: 40,      // 40%
    productDetail: 20,    // 20%
    popularProducts: 10,  // 10%
    cartAdd: 15,          // 15%
    orderCreate: 10,      // 10%
    couponIssue: 5,       // 5%
};

// 가중치 기반 랜덤 선택
function selectWorkload() {
    const rand = Math.random() * 100;
    let cumulative = 0;

    for (const [workload, weight] of Object.entries(WORKLOAD_WEIGHTS)) {
        cumulative += weight;
        if (rand < cumulative) {
            return workload;
        }
    }
    return 'productList';  // fallback
}

// API 호출 함수들
function getProductList() {
    const page = Math.floor(Math.random() * 5);
    const response = http.get(
        `${BASE_URL}/api/products?page=${page}&size=20`,
        { headers: DEFAULT_HEADERS, tags: { name: 'product_list' } }
    );
    productListDuration.add(response.timings.duration);
    return response;
}

function getProductDetail() {
    const productId = getRandomProductId();
    const response = http.get(
        `${BASE_URL}/api/products/${productId}`,
        { headers: DEFAULT_HEADERS, tags: { name: 'product_detail' } }
    );
    productDetailDuration.add(response.timings.duration);
    return response;
}

function getPopularProducts() {
    const response = http.get(
        `${BASE_URL}/api/products/popular`,
        { headers: DEFAULT_HEADERS, tags: { name: 'popular_products' } }
    );
    popularProductDuration.add(response.timings.duration);
    return response;
}

function addToCart(userId) {
    const payload = JSON.stringify({
        productOptionId: getRandomProductOptionId(),
        quantity: getRandomQuantity(),
    });

    const response = http.post(
        `${BASE_URL}/api/carts?userId=${userId}`,
        payload,
        { headers: DEFAULT_HEADERS, tags: { name: 'cart_add' } }
    );
    cartAddDuration.add(response.timings.duration);
    return response;
}

function createOrder(userId) {
    // 먼저 포인트 충전
    http.post(
        `${BASE_URL}/api/point/charge?userId=${userId}`,
        JSON.stringify({ amount: 500000 }),
        { headers: DEFAULT_HEADERS }
    );

    // 장바구니 추가
    http.post(
        `${BASE_URL}/api/carts?userId=${userId}`,
        JSON.stringify({
            productOptionId: getRandomProductOptionId(),
            quantity: 1,
        }),
        { headers: DEFAULT_HEADERS }
    );

    // 주문 생성
    const response = http.post(
        `${BASE_URL}/api/orders?userId=${userId}`,
        JSON.stringify({ userCouponId: null }),
        { headers: DEFAULT_HEADERS, tags: { name: 'order_create' } }
    );
    orderCreateDuration.add(response.timings.duration);
    return response;
}

function issueCoupon(userId) {
    const couponId = Math.floor(Math.random() * 5) + 1;  // 1-5
    const response = http.post(
        `${BASE_URL}/api/coupons/${couponId}/issue?userId=${userId}`,
        null,
        { headers: DEFAULT_HEADERS, tags: { name: 'coupon_issue' } }
    );
    couponIssueDuration.add(response.timings.duration);
    return response;
}

// 메인 테스트 함수
export default function() {
    const userId = getRandomUserId() + (__VU * 1000) + (__ITER * 10);
    const workload = selectWorkload();

    requestCounter.add(1);

    let response;
    let isReadOperation = true;

    switch (workload) {
        case 'productList':
            group('상품 목록 조회', function() {
                response = getProductList();
            });
            break;

        case 'productDetail':
            group('상품 상세 조회', function() {
                response = getProductDetail();
            });
            break;

        case 'popularProducts':
            group('인기 상품 조회', function() {
                response = getPopularProducts();
            });
            break;

        case 'cartAdd':
            isReadOperation = false;
            group('장바구니 추가', function() {
                response = addToCart(userId);
            });
            break;

        case 'orderCreate':
            isReadOperation = false;
            group('주문 생성', function() {
                response = createOrder(userId);
            });
            break;

        case 'couponIssue':
            isReadOperation = false;
            group('쿠폰 발급', function() {
                response = issueCoupon(userId);
            });
            break;
    }

    // 성공률 체크
    const isSuccess = response && response.status === 200;
    apiSuccessRate.add(isSuccess ? 1 : 0);

    if (isReadOperation) {
        readSuccessRate.add(isSuccess ? 1 : 0);
    } else {
        writeSuccessRate.add(isSuccess ? 1 : 0);
    }

    check(response, {
        'status is 2xx': (r) => r && r.status >= 200 && r.status < 300,
        'response time < 3s': (r) => r && r.timings.duration < 3000,
    });

    // 사용자 행동 시뮬레이션을 위한 짧은 대기
    sleep(Math.random() * 2 + 0.5);  // 0.5 ~ 2.5초
}

// 테스트 완료 후 요약
export function handleSummary(data) {
    console.log('\n========== 혼합 워크로드 테스트 결과 ==========');
    console.log('');
    console.log('=== API별 응답시간 (p95) ===');

    const metrics = data.metrics;

    if (metrics.product_list_duration) {
        console.log(`상품 목록 조회: ${metrics.product_list_duration.values['p(95)'].toFixed(2)}ms`);
    }
    if (metrics.product_detail_duration) {
        console.log(`상품 상세 조회: ${metrics.product_detail_duration.values['p(95)'].toFixed(2)}ms`);
    }
    if (metrics.popular_product_duration) {
        console.log(`인기 상품 조회: ${metrics.popular_product_duration.values['p(95)'].toFixed(2)}ms`);
    }
    if (metrics.cart_add_duration) {
        console.log(`장바구니 추가: ${metrics.cart_add_duration.values['p(95)'].toFixed(2)}ms`);
    }
    if (metrics.order_create_duration) {
        console.log(`주문 생성: ${metrics.order_create_duration.values['p(95)'].toFixed(2)}ms`);
    }
    if (metrics.coupon_issue_duration) {
        console.log(`쿠폰 발급: ${metrics.coupon_issue_duration.values['p(95)'].toFixed(2)}ms`);
    }

    console.log('');
    console.log('=== 성공률 ===');
    if (metrics.api_success_rate) {
        console.log(`전체 API: ${(metrics.api_success_rate.values.rate * 100).toFixed(2)}%`);
    }
    if (metrics.read_success_rate) {
        console.log(`읽기 API: ${(metrics.read_success_rate.values.rate * 100).toFixed(2)}%`);
    }
    if (metrics.write_success_rate) {
        console.log(`쓰기 API: ${(metrics.write_success_rate.values.rate * 100).toFixed(2)}%`);
    }

    console.log('==============================================\n');

    return {
        'results/mixed-workload-result.json': JSON.stringify(data, null, 2),
    };
}
