# k6 부하 테스트 스크립트

HH E-Commerce 시스템의 부하 테스트를 위한 k6 스크립트입니다.

## 사전 요구사항

### k6 설치

```bash
# macOS
brew install k6

# Windows (chocolatey)
choco install k6

# Docker
docker pull grafana/k6
```

### 테스트 환경 실행

```bash
# Docker Compose로 인프라 실행
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

## 테스트 시나리오

| 시나리오 | 파일 | 목적 |
|---------|------|------|
| 동시 주문 테스트 | `01-concurrent-order-test.js` | 재고 데이터 무결성 검증 |
| 쿠폰 발급 테스트 | `02-coupon-issue-test.js` | 선착순 쿠폰 정확성 검증 |
| 혼합 워크로드 | `03-mixed-workload-test.js` | 실제 트래픽 패턴 시뮬레이션 |
| 스트레스 테스트 | `04-stress-test.js` | 시스템 임계점 파악 |

## 실행 방법

### 개별 테스트 실행

```bash
# 동시 주문 테스트
k6 run scenarios/01-concurrent-order-test.js

# 선착순 쿠폰 발급 테스트
k6 run scenarios/02-coupon-issue-test.js

# 혼합 워크로드 테스트
k6 run scenarios/03-mixed-workload-test.js

# 스트레스 테스트
k6 run scenarios/04-stress-test.js
```

### 환경변수 설정

```bash
# 커스텀 설정으로 실행
k6 run -e BASE_URL=http://localhost:8080 \
       -e VUS=200 \
       -e DURATION=5m \
       scenarios/03-mixed-workload-test.js
```

### 전체 테스트 실행

```bash
# 실행 스크립트 사용
./run-all-tests.sh
```

## 환경변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `BASE_URL` | API 서버 URL | `http://localhost:8080` |
| `VUS` | 가상 사용자 수 | 시나리오별 상이 |
| `DURATION` | 테스트 지속 시간 | 시나리오별 상이 |
| `COUPON_ID` | 테스트 쿠폰 ID | `1` |
| `USER_COUNT` | 테스트 사용자 수 | `1000` |

## 결과 확인

테스트 완료 후 `results/` 디렉토리에 JSON 형식의 상세 결과가 저장됩니다.

```
results/
├── concurrent-order-result.json
├── coupon-issue-result.json
├── mixed-workload-result.json
└── stress-test-result.json
```

## 시나리오별 상세

### 1. 동시 주문 테스트

- **목적**: 동일 상품에 대한 동시 주문 시 재고 무결성 검증
- **VU**: 100명
- **검증 포인트**:
  - 재고 초과 판매 없음
  - 주문 중복 생성 없음
  - 성공률 80% 이상

### 2. 선착순 쿠폰 발급 테스트

- **목적**: 제한된 쿠폰의 정확한 발급 검증
- **VU**: 500~1000명
- **검증 포인트**:
  - 발급 수량 = 쿠폰 총 수량
  - 중복 발급 없음
  - 초과 발급 없음

### 3. 혼합 워크로드 테스트

- **목적**: 실제 서비스 트래픽 패턴 시뮬레이션
- **트래픽 비율**:
  - 읽기 70% (상품 조회)
  - 쓰기 25% (장바구니, 주문)
  - 이벤트 5% (쿠폰)
- **검증 포인트**:
  - 전체 에러율 < 5%
  - 읽기 API p95 < 300ms
  - 쓰기 API p95 < 2000ms

### 4. 스트레스 테스트

- **목적**: 시스템 처리 한계 파악
- **부하 패턴**: 50 → 500 VU 점진 증가
- **검증 포인트**:
  - 임계점 식별 (응답시간 급증 시점)
  - 복구 가능성 확인
  - 최대 처리량 (RPS) 측정

## Docker로 실행

```bash
# 기본 실행
docker run -i --network host grafana/k6 run - <scenarios/01-concurrent-order-test.js

# 볼륨 마운트하여 실행
docker run -i --network host \
  -v $(pwd):/scripts \
  grafana/k6 run /scripts/scenarios/01-concurrent-order-test.js
```

## 모니터링 연동 (선택사항)

### Grafana + InfluxDB

```bash
# InfluxDB로 메트릭 전송
k6 run --out influxdb=http://localhost:8086/k6 scenarios/04-stress-test.js
```

### Prometheus

```bash
# Prometheus Remote Write
k6 run --out experimental-prometheus-rw scenarios/04-stress-test.js
```

## 트러블슈팅

### 연결 오류

```
ERRO[0000] dial tcp: connection refused
```

→ 애플리케이션이 실행 중인지 확인

### 메모리 부족

```bash
# k6 메모리 제한 조정
K6_BROWSER_HEADLESS=true k6 run --compatibility-mode=extended ...
```

### 타임아웃

테스트 시나리오의 `timeout` 옵션을 조정하세요.
