/**
 * 시나리오 4: 스트레스 테스트 (Stress Test)
 *
 * 목적: 시스템의 처리 한계 및 임계점 파악
 *
 * 부하 증가 패턴:
 * - 0-2분: 50 VU (워밍업)
 * - 2-4분: 100 VU
 * - 4-6분: 200 VU
 * - 6-8분: 300 VU
 * - 8-10분: 400 VU
 * - 10-12분: 500 VU (최대 부하)
 * - 12-15분: 500 VU 유지
 *
 * 실행 방법:
 * k6 run scenarios/04-stress-test.js
 *
 * 환경변수:
 * - BASE_URL: API 서버 URL (기본: http://localhost:8080)
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend, Rate, Gauge } from 'k6/metrics';
import {
    BASE_URL,
    DEFAULT_HEADERS,
    getRandomProductId,
    getRandomProductOptionId,
} from '../config.js';

// 커스텀 메트릭
const responseTime = new Trend('response_time');
const errorRate = new Rate('error_rate');
const throughput = new Counter('throughput');
const currentVUs = new Gauge('current_vus');

// API별 메트릭
const productListRT = new Trend('product_list_rt');
const productDetailRT = new Trend('product_detail_rt');
const cartAddRT = new Trend('cart_add_rt');
const orderCreateRT = new Trend('order_create_rt');

// 테스트 설정 - 단계적 부하 증가
export const options = {
    stages: [
        { duration: '2m', target: 50 },    // 워밍업
        { duration: '2m', target: 100 },   // 단계 1
        { duration: '2m', target: 200 },   // 단계 2
        { duration: '2m', target: 300 },   // 단계 3
        { duration: '2m', target: 400 },   // 단계 4
        { duration: '2m', target: 500 },   // 단계 5 (최대)
        { duration: '3m', target: 500 },   // 유지
        { duration: '2m', target: 0 },     // 쿨다운
    ],
    thresholds: {
        'response_time': ['p(95)<3000', 'p(99)<5000'],
        'error_rate': ['rate<0.3'],
        'http_req_duration': ['p(95)<3000'],
    },
};

// API 호출 함수들
function callProductList() {
    const start = new Date().getTime();
    const response = http.get(
        `${BASE_URL}/api/products?page=0&size=20`,
        { headers: DEFAULT_HEADERS, tags: { name: 'stress_product_list' } }
    );
    productListRT.add(new Date().getTime() - start);
    return response;
}

function callProductDetail() {
    const start = new Date().getTime();
    const response = http.get(
        `${BASE_URL}/api/products/${getRandomProductId()}`,
        { headers: DEFAULT_HEADERS, tags: { name: 'stress_product_detail' } }
    );
    productDetailRT.add(new Date().getTime() - start);
    return response;
}

function callCartAdd(userId) {
    const start = new Date().getTime();
    const response = http.post(
        `${BASE_URL}/api/carts?userId=${userId}`,
        JSON.stringify({
            productOptionId: getRandomProductOptionId(),
            quantity: 1,
        }),
        { headers: DEFAULT_HEADERS, tags: { name: 'stress_cart_add' } }
    );
    cartAddRT.add(new Date().getTime() - start);
    return response;
}

function callOrderCreate(userId) {
    // 사전 준비: 포인트 충전
    http.post(
        `${BASE_URL}/api/point/charge?userId=${userId}`,
        JSON.stringify({ amount: 500000 }),
        { headers: DEFAULT_HEADERS }
    );

    // 사전 준비: 장바구니 추가
    http.post(
        `${BASE_URL}/api/carts?userId=${userId}`,
        JSON.stringify({
            productOptionId: getRandomProductOptionId(),
            quantity: 1,
        }),
        { headers: DEFAULT_HEADERS }
    );

    const start = new Date().getTime();
    const response = http.post(
        `${BASE_URL}/api/orders?userId=${userId}`,
        JSON.stringify({ userCouponId: null }),
        { headers: DEFAULT_HEADERS, tags: { name: 'stress_order_create' } }
    );
    orderCreateRT.add(new Date().getTime() - start);
    return response;
}

// 메인 테스트 함수
export default function() {
    currentVUs.add(__VU);

    const userId = __VU * 10000 + __ITER;

    // 워크로드 혼합: 읽기 80%, 쓰기 20%
    const rand = Math.random() * 100;
    let response;

    if (rand < 50) {
        // 상품 목록 조회 (50%)
        group('스트레스 - 상품 목록', function() {
            response = callProductList();
        });
    } else if (rand < 80) {
        // 상품 상세 조회 (30%)
        group('스트레스 - 상품 상세', function() {
            response = callProductDetail();
        });
    } else if (rand < 95) {
        // 장바구니 추가 (15%)
        group('스트레스 - 장바구니', function() {
            response = callCartAdd(userId);
        });
    } else {
        // 주문 생성 (5%)
        group('스트레스 - 주문', function() {
            response = callOrderCreate(userId);
        });
    }

    // 메트릭 기록
    throughput.add(1);
    responseTime.add(response.timings.duration);

    const isError = response.status >= 400 || response.status === 0;
    errorRate.add(isError ? 1 : 0);

    // 체크
    check(response, {
        'status is not 5xx': (r) => r.status < 500,
        'response received': (r) => r.status !== 0,
        'response time < 5s': (r) => r.timings.duration < 5000,
    });

    // 에러 발생 시 로깅 (샘플링)
    if (isError && Math.random() < 0.01) {
        console.log(`[VU:${__VU}] Error: ${response.status} - ${response.body}`);
    }

    // 요청 간격
    sleep(Math.random() * 0.5 + 0.1);
}

// 테스트 완료 후 요약
export function handleSummary(data) {
    const metrics = data.metrics;

    console.log('\n========== 스트레스 테스트 결과 ==========');
    console.log('');
    console.log('=== 전체 성능 지표 ===');

    if (metrics.http_reqs) {
        console.log(`총 요청 수: ${metrics.http_reqs.values.count}`);
        console.log(`초당 요청 수 (RPS): ${metrics.http_reqs.values.rate.toFixed(2)}`);
    }

    if (metrics.response_time) {
        console.log('');
        console.log('=== 응답 시간 ===');
        console.log(`평균: ${metrics.response_time.values.avg.toFixed(2)}ms`);
        console.log(`중앙값 (p50): ${metrics.response_time.values['p(50)'].toFixed(2)}ms`);
        console.log(`p95: ${metrics.response_time.values['p(95)'].toFixed(2)}ms`);
        console.log(`p99: ${metrics.response_time.values['p(99)'].toFixed(2)}ms`);
        console.log(`최대: ${metrics.response_time.values.max.toFixed(2)}ms`);
    }

    if (metrics.error_rate) {
        console.log('');
        console.log('=== 에러율 ===');
        console.log(`에러율: ${(metrics.error_rate.values.rate * 100).toFixed(2)}%`);
    }

    console.log('');
    console.log('=== API별 응답시간 (p95) ===');
    if (metrics.product_list_rt) {
        console.log(`상품 목록: ${metrics.product_list_rt.values['p(95)'].toFixed(2)}ms`);
    }
    if (metrics.product_detail_rt) {
        console.log(`상품 상세: ${metrics.product_detail_rt.values['p(95)'].toFixed(2)}ms`);
    }
    if (metrics.cart_add_rt) {
        console.log(`장바구니: ${metrics.cart_add_rt.values['p(95)'].toFixed(2)}ms`);
    }
    if (metrics.order_create_rt) {
        console.log(`주문 생성: ${metrics.order_create_rt.values['p(95)'].toFixed(2)}ms`);
    }

    console.log('');
    console.log('=== 임계점 분석 ===');
    console.log('아래 지표를 확인하여 시스템 임계점을 파악하세요:');
    console.log('1. 응답시간이 급격히 증가하는 VU 수');
    console.log('2. 에러율이 5%를 넘어가는 시점');
    console.log('3. p99 응답시간이 3초를 넘어가는 시점');
    console.log('==========================================\n');

    return {
        'results/stress-test-result.json': JSON.stringify(data, null, 2),
    };
}
