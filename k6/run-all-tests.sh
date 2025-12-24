#!/bin/bash

# HH E-Commerce 부하 테스트 전체 실행 스크립트
# 사용법: ./run-all-tests.sh [BASE_URL]

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 기본 설정
BASE_URL="${1:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"

# 결과 디렉토리 생성
mkdir -p "${RESULTS_DIR}"

# 시작 시간
START_TIME=$(date +%s)

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}   HH E-Commerce 부하 테스트 시작${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""
echo -e "API 서버: ${GREEN}${BASE_URL}${NC}"
echo -e "결과 저장: ${GREEN}${RESULTS_DIR}${NC}"
echo ""

# 서버 상태 확인
echo -e "${YELLOW}[1/5] 서버 상태 확인 중...${NC}"
if curl -s --connect-timeout 5 "${BASE_URL}/api/products?page=0&size=1" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 서버 정상 동작 중${NC}"
else
    echo -e "${RED}✗ 서버 연결 실패. 서버가 실행 중인지 확인하세요.${NC}"
    echo -e "  URL: ${BASE_URL}"
    exit 1
fi
echo ""

# 테스트 1: 동시 주문 테스트 (간소화 버전)
echo -e "${YELLOW}[2/5] 동시 주문 테스트 실행 중...${NC}"
k6 run \
    -e BASE_URL="${BASE_URL}" \
    -e VUS=50 \
    -e ITERATIONS=50 \
    --summary-export="${RESULTS_DIR}/concurrent-order-summary.json" \
    "${SCRIPT_DIR}/scenarios/01-concurrent-order-test.js" || true
echo -e "${GREEN}✓ 동시 주문 테스트 완료${NC}"
echo ""

# 테스트 2: 쿠폰 발급 테스트 (간소화 버전)
echo -e "${YELLOW}[3/5] 선착순 쿠폰 발급 테스트 실행 중...${NC}"
k6 run \
    -e BASE_URL="${BASE_URL}" \
    -e VUS=100 \
    -e ITERATIONS=200 \
    -e COUPON_ID=1 \
    --summary-export="${RESULTS_DIR}/coupon-issue-summary.json" \
    "${SCRIPT_DIR}/scenarios/02-coupon-issue-test.js" || true
echo -e "${GREEN}✓ 쿠폰 발급 테스트 완료${NC}"
echo ""

# 테스트 3: 혼합 워크로드 테스트 (간소화 버전)
echo -e "${YELLOW}[4/5] 혼합 워크로드 테스트 실행 중...${NC}"
k6 run \
    -e BASE_URL="${BASE_URL}" \
    -e VUS=50 \
    -e DURATION=2m \
    --summary-export="${RESULTS_DIR}/mixed-workload-summary.json" \
    "${SCRIPT_DIR}/scenarios/03-mixed-workload-test.js" || true
echo -e "${GREEN}✓ 혼합 워크로드 테스트 완료${NC}"
echo ""

# 테스트 4: 스트레스 테스트 (간소화 버전 - 시간 단축)
echo -e "${YELLOW}[5/5] 스트레스 테스트 실행 중 (간소화)...${NC}"
# 간소화된 스테이지로 실행
k6 run \
    -e BASE_URL="${BASE_URL}" \
    --stage 30s:50 \
    --stage 30s:100 \
    --stage 30s:150 \
    --stage 30s:100 \
    --stage 30s:0 \
    --summary-export="${RESULTS_DIR}/stress-test-summary.json" \
    "${SCRIPT_DIR}/scenarios/04-stress-test.js" || true
echo -e "${GREEN}✓ 스트레스 테스트 완료${NC}"
echo ""

# 종료 시간 및 소요 시간 계산
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
MINUTES=$((DURATION / 60))
SECONDS=$((DURATION % 60))

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}   테스트 완료${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""
echo -e "소요 시간: ${GREEN}${MINUTES}분 ${SECONDS}초${NC}"
echo -e "결과 파일: ${GREEN}${RESULTS_DIR}/${NC}"
echo ""
echo "결과 파일 목록:"
ls -la "${RESULTS_DIR}/"
echo ""
echo -e "${YELLOW}상세 분석을 위해 결과 JSON 파일을 확인하세요.${NC}"
