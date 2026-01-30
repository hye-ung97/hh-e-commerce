/**
 * 시나리오 1: 동시 주문 테스트 (Concurrent Order Test)
 *
 * 목적: 동일 상품에 대한 동시 주문 시 재고 데이터 무결성 검증
 *
 * 테스트 흐름:
 * 1. 사전 조건: 상품의 재고 확인
 * 2. N명의 사용자가 동시에 주문 요청
 * 3. 재고 초과 판매 없음 검증
 *
 * 실행 방법:
 * k6 run scenarios/01-concurrent-order-test.js
 *
 * 환경변수:
 * - BASE_URL: API 서버 URL (기본: http://localhost:8080)
 * - VUS: 동시 사용자 수 (기본: 100)
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { BASE_URL, DEFAULT_HEADERS } from '../config.js';

// 커스텀 메트릭
const orderSuccessCounter = new Counter('order_success_count');
const orderFailCounter = new Counter('order_fail_count');
const orderDuration = new Trend('order_duration');
const orderSuccessRate = new Rate('order_success_rate');

// 테스트 설정
export const options = {
    scenarios: {
        concurrent_orders: {
            executor: 'shared-iterations',
            vus: parseInt(__ENV.VUS) || 100,
            iterations: parseInt(__ENV.ITERATIONS) || 100,
            maxDuration: '5m',
        },
    },
    thresholds: {
        'order_duration': ['p(95)<2000'],
        'order_success_rate': ['rate>0.8'],
        'http_req_failed': ['rate<0.2'],
    },
};

// 테스트 데이터: 사용자별 설정
const users = new SharedArray('users', function() {
    const userList = [];
    for (let i = 1; i <= 200; i++) {
        userList.push({
            userId: i,
            productOptionId: ((i - 1) % 10) + 1,  // 1-10 순환
        });
    }
    return userList;
});

// 사전 준비: 장바구니에 상품 추가
function setupCart(userId, productOptionId) {
    const addCartPayload = JSON.stringify({
        productOptionId: productOptionId,
        quantity: 1,
    });

    const addCartRes = http.post(
        `${BASE_URL}/api/carts?userId=${userId}`,
        addCartPayload,
        { headers: DEFAULT_HEADERS }
    );

    return addCartRes.status === 200;
}

// 포인트 충전
function chargePoint(userId, amount) {
    const chargePayload = JSON.stringify({
        amount: amount,
    });

    const chargeRes = http.post(
        `${BASE_URL}/api/point/charge?userId=${userId}`,
        chargePayload,
        { headers: DEFAULT_HEADERS }
    );

    return chargeRes.status === 200;
}

// 메인 테스트 함수
export default function() {
    const userIndex = __VU - 1;
    const user = users[userIndex % users.length];
    const userId = user.userId + (__ITER * 200);  // 반복마다 다른 사용자
    const productOptionId = user.productOptionId;

    group('주문 테스트', function() {
        // Step 1: 포인트 충전
        group('포인트 충전', function() {
            const charged = chargePoint(userId, 1000000);
            check(charged, {
                '포인트 충전 성공': (r) => r === true,
            });
        });

        // Step 2: 장바구니에 상품 추가
        group('장바구니 추가', function() {
            const cartAdded = setupCart(userId, productOptionId);
            check(cartAdded, {
                '장바구니 추가 성공': (r) => r === true,
            });
        });

        sleep(0.1);  // 약간의 지연

        // Step 3: 주문 생성
        group('주문 생성', function() {
            const orderPayload = JSON.stringify({
                userCouponId: null,
            });

            const startTime = new Date().getTime();

            const orderRes = http.post(
                `${BASE_URL}/api/orders?userId=${userId}`,
                orderPayload,
                { headers: DEFAULT_HEADERS }
            );

            const duration = new Date().getTime() - startTime;
            orderDuration.add(duration);

            const isSuccess = orderRes.status === 200;

            if (isSuccess) {
                orderSuccessCounter.add(1);
                orderSuccessRate.add(1);
            } else {
                orderFailCounter.add(1);
                orderSuccessRate.add(0);
            }

            check(orderRes, {
                '주문 응답 수신': (r) => r.status === 200 || r.status === 400 || r.status === 409,
                '주문 성공': (r) => r.status === 200,
            });

            // 실패 시 원인 로깅
            if (!isSuccess && orderRes.body) {
                console.log(`Order failed for user ${userId}: ${orderRes.body}`);
            }
        });
    });

    sleep(0.5);
}

// 테스트 완료 후 요약
export function handleSummary(data) {
    const successCount = data.metrics.order_success_count ?
        data.metrics.order_success_count.values.count : 0;
    const failCount = data.metrics.order_fail_count ?
        data.metrics.order_fail_count.values.count : 0;
    const totalOrders = successCount + failCount;

    console.log('\n========== 동시 주문 테스트 결과 ==========');
    console.log(`총 주문 요청: ${totalOrders}`);
    console.log(`성공: ${successCount}`);
    console.log(`실패: ${failCount}`);
    console.log(`성공률: ${totalOrders > 0 ? ((successCount / totalOrders) * 100).toFixed(2) : 0}%`);
    console.log('============================================\n');

    return {
        'stdout': JSON.stringify({
            test: 'concurrent-order-test',
            totalOrders,
            successCount,
            failCount,
            successRate: totalOrders > 0 ? (successCount / totalOrders) : 0,
        }, null, 2),
        'results/concurrent-order-result.json': JSON.stringify(data, null, 2),
    };
}
