# HH-Plus E-Commerce

Spring Boot 기반의 이커머스 백엔드 시스템입니다.

---

## 기술 스택

- **Language**: Java 17
- **Framework**: Spring Boot 3.5.7
- **Database**: MySQL 8.0, H2 (테스트)
- **Cache/Lock**: Redis, Redisson
- **ORM**: Spring Data JPA
- **API Docs**: SpringDoc OpenAPI (Swagger)
- **Monitoring**: Spring Actuator, Micrometer (Prometheus)
- **Test**: JUnit 5, Testcontainers
- **Build**: Gradle

---

## 시작하기

### 사전 요구사항

- Java 17+
- MySQL 8.0+
- Redis 6.0+
- Gradle 8.0+

### 인프라 실행

```bash
# MySQL 실행 (Docker)
docker run -d --name mysql-ecommerce \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=ecommerce \
  -e MYSQL_USER=ecommerce_user \
  -e MYSQL_PASSWORD=ecommerce_pass123 \
  -p 3306:3306 \
  mysql:8.0

# Redis 실행 (Docker)
docker run -d --name redis-ecommerce \
  -p 6379:6379 \
  redis:7-alpine
```

### 애플리케이션 실행

```bash
# 빌드
./gradlew clean build

# 실행
./gradlew bootRun
```

### API 문서 확인

애플리케이션 실행 후 접속:
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

---

## 디렉토리 구조

```
src/main/java/org/hhplus/hhecommerce/
├── api/                        # Presentation Layer
│   ├── controller/             # REST API 엔드포인트
│   ├── dto/                    # 요청/응답 DTO
│   └── exception/              # 전역 예외 처리
│
├── application/                # Application Layer
│   ├── cart/                   # 장바구니 UseCase
│   ├── coupon/                 # 쿠폰 UseCase
│   ├── order/                  # 주문 UseCase
│   ├── point/                  # 포인트 UseCase
│   ├── product/                # 상품 UseCase
│   └── ranking/                # 상품 랭킹 UseCase
│
├── domain/                     # Domain Layer
│   ├── cart/                   # 장바구니 도메인
│   ├── coupon/                 # 쿠폰 도메인
│   ├── order/                  # 주문 도메인
│   ├── point/                  # 포인트 도메인
│   ├── product/                # 상품 도메인
│   ├── ranking/                # 랭킹 도메인
│   └── user/                   # 사용자 도메인
│
└── infrastructure/             # Infrastructure Layer
    ├── config/                 # Redis, JPA 등 설정
    ├── coupon/                 # Redis 기반 쿠폰 발급 구현체
    ├── ranking/                # Redis 기반 랭킹 구현체
    ├── repository/             # JPA Repository 구현체
    └── scheduler/              # 캐시 갱신 스케줄러
```

### 레이어별 역할

- **API**: REST API 엔드포인트, 요청 검증 및 응답 변환, 전역 예외 처리
- **Application**: 비즈니스 유스케이스 구현, 트랜잭션 경계 설정, 동시성 제어
- **Domain**: 핵심 비즈니스 로직, 도메인 엔티티/밸류, Repository 인터페이스
- **Infrastructure**: 외부 시스템 통합, Repository 구현체, Redis/DB 설정

---

## 주요 기능

### 상품 (Product)

- **상품 목록 조회**: 페이징 처리, Redis 캐시 적용 (TTL 10분)
- **상품 상세 조회**: 옵션별 재고 정보 포함, Redis 캐시 적용 (TTL 5분)
- **인기 상품 조회**: 최근 3일 판매량 기준 Top 5, 스케줄러 기반 캐시 갱신
- **실시간 랭킹**: Redis Sorted Set 기반 일간/주간 판매 랭킹

### 주문 (Order)

- **주문 생성**: 재고 차감, 쿠폰 적용, 포인트 결제를 원자적으로 처리
- **주문 조회**: 사용자별 주문 목록 및 상세 조회
- **동시성 제어**: 사용자별 분산 락 + DB Atomic Update로 이중 방어

### 쿠폰 (Coupon)

- **쿠폰 발급**: Redis Lua 스크립트 기반 선착순 발급
- **쿠폰 조회**: 발급 가능한 쿠폰, 보유 쿠폰, 사용 가능한 쿠폰 조회
- **동시성 제어**: Redis + Pending 상태 관리로 초과 발급 방지

### 포인트 (Point)

- **포인트 조회**: 사용자 잔액 조회
- **포인트 충전**: 지정 금액 충전
- **포인트 차감**: 주문 시 자동 차감, Atomic Update로 동시성 제어

### 장바구니 (Cart)

- **장바구니 조회**: 사용자별 상품 목록 조회
- **상품 추가/수정/삭제**: 수량 변경 및 항목 관리

---

## API 엔드포인트

### 상품 API

| Method | Endpoint                       | 설명                 |
|--------|--------------------------------|----------------------|
| GET    | `/api/products`                | 상품 목록 조회       |
| GET    | `/api/products/{productId}`    | 상품 상세 조회       |
| GET    | `/api/products/popular`        | 인기 상품 조회       |
| GET    | `/api/products/ranking/daily`  | 실시간 일간 랭킹     |
| GET    | `/api/products/ranking/weekly` | 실시간 주간 랭킹     |

### 주문 API

| Method | Endpoint               | 설명             |
|--------|------------------------|------------------|
| POST   | `/api/orders`          | 주문 생성        |
| GET    | `/api/orders`          | 주문 목록 조회   |
| GET    | `/api/orders/{orderId}`| 주문 상세 조회   |

### 쿠폰 API

| Method | Endpoint                            | 설명                   |
|--------|-------------------------------------|------------------------|
| GET    | `/api/coupons`                      | 발급 가능한 쿠폰 조회  |
| POST   | `/api/coupons/{couponId}/issue`     | 쿠폰 발급              |
| GET    | `/api/coupons/users/coupons`        | 보유 쿠폰 조회         |
| GET    | `/api/coupons/users/coupons/available` | 사용 가능한 쿠폰 조회 |

### 포인트 API

| Method | Endpoint           | 설명          |
|--------|--------------------|---------------|
| GET    | `/api/point`       | 포인트 조회   |
| POST   | `/api/point/charge`| 포인트 충전   |
| POST   | `/api/point/deduct`| 포인트 차감   |

### 장바구니 API

| Method | Endpoint            | 설명          |
|--------|---------------------|---------------|
| GET    | `/api/carts`        | 장바구니 조회 |
| POST   | `/api/carts`        | 상품 추가     |
| PATCH  | `/api/carts/{cartId}`| 수량 변경    |
| DELETE | `/api/carts/{cartId}`| 상품 삭제    |

---

## 아키텍처 설계

### 동시성 제어 전략

#### 쿠폰 발급
- **전략**: Redis Lua Script + Pending 상태 관리
- **구현**: 원자적 재고 확인/차감, DB 저장 실패 시 자동 롤백

#### 주문 생성
- **전략**: 사용자별 분산 락 + DB Atomic Update
- **구현**: Redisson Lock + WHERE 조건 검증

#### 재고 차감
- **전략**: DB Atomic Update
- **구현**: `UPDATE ... WHERE stock >= quantity`

#### 포인트 차감
- **전략**: 낙관적 락 + Retry
- **구현**: `@Version` + `@Retryable`

### 캐싱 전략

| 대상           | 전략              | TTL    |
|----------------|-------------------|--------|
| 상품 목록      | Look-Aside        | 10분   |
| 상품 상세      | Look-Aside        | 5분    |
| 인기 상품      | Scheduled Refresh | 26시간 |
| 실시간 랭킹    | Redis Sorted Set  | -      |

### 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Request                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    API Layer (Controller)                   │
│                    - Request Validation                     │
│                    - Response Transformation                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 Application Layer (UseCase)                 │
│                 - Transaction Boundary                      │
│                 - Distributed Lock (Redisson)               │
│                 - Cache (@Cacheable)                        │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌──────────────────────────┐    ┌──────────────────────────┐
│      Redis               │    │      MySQL               │
│  - Cache (products)      │    │  - Master Data           │
│  - Distributed Lock      │    │  - Pessimistic Lock      │
│  - Coupon Stock          │    │  - Atomic Update         │
│  - Ranking (Sorted Set)  │    │  - Unique Constraint     │
└──────────────────────────┘    └──────────────────────────┘
```

---

## 테스트

### 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# JaCoCo 커버리지 리포트 생성
# 위치: build/reports/jacoco/test/html/index.html
```

### 테스트 구성

- **Integration Test**: Controller 레이어 통합 테스트 (Testcontainers 사용)
- **UseCase Test**: Application 레이어 단위 테스트
- **Domain Test**: Domain 엔티티 비즈니스 로직 테스트
- **Concurrency Test**: 동시성 제어 검증 테스트
- **Infrastructure Test**: Redis 기반 구현체 테스트

### 커버리지 요약

| 측정 항목    | 커버리지 |
|--------------|----------|
| Instructions | 92%      |
| Branches     | 88%      |
| Lines        | 93%      |
| Classes      | 100%     |

---

## 관련 문서

- [동시성 제어](docs/concurrency.md) - 주문/쿠폰 발급의 동시성 문제 분석 및 해결 방안
- [Redis 성능 개선](docs/redis-performance-report.md) - 캐시 전략, 분산 락 구현 상세
- [쿼리 최적화](docs/QUERY_OPTIMIZATION_REPORT.md) - N+1 문제 해결, 인덱스 최적화
- [시스템 설계 회고](docs/system-design-retrospective.md) - 설계 결정 및 개선 사항

---

## 환경 설정

### application.properties

```properties
# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/ecommerce
spring.datasource.username=ecommerce_user
spring.datasource.password=ecommerce_pass123

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.cache.type=redis

# Swagger
springdoc.swagger-ui.path=/swagger-ui
```

### 쿠폰 발급 전략 설정

```properties
# Redis Lua Script 기반 (기본값)
coupon.issue.strategy=redis

# Redisson 분산 락 기반
coupon.issue.strategy=redisson
```
