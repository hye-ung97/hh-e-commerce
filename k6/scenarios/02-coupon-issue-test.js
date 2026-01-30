/**
 * 시나리오 2: 선착순 쿠폰 발급 테스트 (First-Come Coupon Test)
 *
 * 목적: 제한된 수량의 쿠폰에 대한 동시 발급 요청 시 정확성 검증
 *
 * 테스트 흐름:
 * 1. 사전 조건: 쿠폰 수량 = N개
 * 2. M명의 사용자가 동시에 쿠폰 발급 요청
 * 3. 정확히 N명만 발급 성공 검증
 *
 * 실행 방법:
 * k6 run scenarios/02-coupon-issue-test.js
 *
 * 환경변수:
 * - BASE_URL: API 서버 URL (기본: http://localhost:8080)
 * - VUS: 동시 사용자 수 (기본: 1000)
 * - COUPON_ID: 테스트할 쿠폰 ID (기본: 1)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { BASE_URL, DEFAULT_HEADERS, TEST_COUPON_ID } from '../config.js';

// 커스텀 메트릭
const couponSuccessCounter = new Counter('coupon_success_count');
const couponFailCounter = new Counter('coupon_fail_count');
const couponSoldOutCounter = new Counter('coupon_sold_out_count');
const couponDuplicateCounter = new Counter('coupon_duplicate_count');
const couponDuration = new Trend('coupon_issue_duration');
const couponSuccessRate = new Rate('coupon_success_rate');

// 테스트 설정
export const options = {
    scenarios: {
        coupon_rush: {
            executor: 'shared-iterations',
            vus: parseInt(__ENV.VUS) || 500,
            iterations: parseInt(__ENV.ITERATIONS) || 1000,
            maxDuration: '3m',
        },
    },
    thresholds: {
        'coupon_issue_duration': ['p(95)<1000', 'p(99)<2000'],
        'http_req_failed': ['rate<0.5'],  // 쿠폰 소진으로 인한 실패 허용
    },
};

// 에러 타입 분류
function classifyError(response) {
    if (response.status === 200) {
        return 'success';
    }

    const body = response.body ? response.body.toLowerCase() : '';

    if (response.status === 409 || body.includes('already') || body.includes('duplicate')) {
        return 'duplicate';
    }

    if (response.status === 400 || body.includes('sold out') || body.includes('exhausted') || body.includes('수량')) {
        return 'sold_out';
    }

    return 'other_error';
}

// 메인 테스트 함수
export default function() {
    // 각 VU에게 고유한 사용자 ID 부여
    const userId = __VU + (__ITER * 1000);
    const couponId = parseInt(__ENV.COUPON_ID) || TEST_COUPON_ID;

    const startTime = new Date().getTime();

    // 쿠폰 발급 요청
    const response = http.post(
        `${BASE_URL}/api/coupons/${couponId}/issue?userId=${userId}`,
        null,
        {
            headers: DEFAULT_HEADERS,
            tags: { name: 'coupon_issue' },
        }
    );

    const duration = new Date().getTime() - startTime;
    couponDuration.add(duration);

    // 결과 분류
    const errorType = classifyError(response);

    switch (errorType) {
        case 'success':
            couponSuccessCounter.add(1);
            couponSuccessRate.add(1);
            break;
        case 'duplicate':
            couponDuplicateCounter.add(1);
            couponSuccessRate.add(0);
            break;
        case 'sold_out':
            couponSoldOutCounter.add(1);
            couponSuccessRate.add(0);
            break;
        default:
            couponFailCounter.add(1);
            couponSuccessRate.add(0);
    }

    // 응답 검증
    check(response, {
        '응답 수신': (r) => r.status !== 0,
        '발급 성공 또는 정상 거부': (r) => [200, 400, 409].includes(r.status),
        '발급 성공': (r) => r.status === 200,
    });

    // 디버그 로깅 (실패 시)
    if (response.status !== 200 && __ITER < 5) {
        console.log(`User ${userId}: ${response.status} - ${response.body}`);
    }

    // 요청 간 미세한 간격
    sleep(0.01);
}

// 테스트 완료 후 요약
export function handleSummary(data) {
    const successCount = data.metrics.coupon_success_count ?
        data.metrics.coupon_success_count.values.count : 0;
    const soldOutCount = data.metrics.coupon_sold_out_count ?
        data.metrics.coupon_sold_out_count.values.count : 0;
    const duplicateCount = data.metrics.coupon_duplicate_count ?
        data.metrics.coupon_duplicate_count.values.count : 0;
    const failCount = data.metrics.coupon_fail_count ?
        data.metrics.coupon_fail_count.values.count : 0;

    const totalRequests = successCount + soldOutCount + duplicateCount + failCount;

    console.log('\n========== 선착순 쿠폰 발급 테스트 결과 ==========');
    console.log(`총 발급 요청: ${totalRequests}`);
    console.log(`발급 성공: ${successCount}`);
    console.log(`수량 소진 거부: ${soldOutCount}`);
    console.log(`중복 발급 거부: ${duplicateCount}`);
    console.log(`기타 실패: ${failCount}`);
    console.log('');
    console.log('=== 검증 포인트 ===');
    console.log(`1. 발급 성공 수가 쿠폰 총 수량과 일치하는가?`);
    console.log(`2. 중복 발급이 없는가? (duplicateCount가 0 또는 합리적 수준)`);
    console.log(`3. 초과 발급이 없는가? (successCount <= 쿠폰 수량)`);
    console.log('===================================================\n');

    return {
        'stdout': JSON.stringify({
            test: 'coupon-issue-test',
            totalRequests,
            successCount,
            soldOutCount,
            duplicateCount,
            failCount,
        }, null, 2),
        'results/coupon-issue-result.json': JSON.stringify(data, null, 2),
    };
}
