# hh-plus E-Commerce 시스템

## 디렉토리 구조

```
src/main/java/org/hhplus/hhecommerce/
├── api/           
│   ├── controller/
│   ├── dto/
│   └── exception/
│
├── application/
│   ├── cart/              
│   ├── coupon/            
│   ├── order/  
│   ├── point/  
│   └── product/
│
├── domain/     
│   ├── cart/   
│   ├── coupon/ 
│   ├── order/  
│   ├── point/  
│   ├── product/
│   ├── user/   
│   └── common/ 
│
└── infrastructure/
    ├── config/  
    └── repository/
```

### 레이어별 역할

#### 1. API Layer (Presentation)
- REST API 엔드포인트 제공
- 요청 검증 및 응답 변환
- 전역 예외 처리

#### 2. Application Layer
- 비즈니스 유스케이스 구현
- 트랜잭션 경계 설정
- 도메인 객체 조합 및 조율
- 동시성 제어 구현 (주문, 쿠폰 발급 등)

#### 3. Domain Layer
- 핵심 비즈니스 로직 및 규칙
- 도메인 엔티티와 밸류 객체
- Repository 인터페이스 정의
- 도메인별 예외 정의

#### 4. Infrastructure Layer
- 외부 시스템과의 통합
- Repository 구현체 (현재 Mock)
- 기술적 설정 및 구성

---

## 테스트 커버리지

### JaCoCo 테스트 커버리지 리포트

**전체 커버리지**: 92% (Instruction Coverage)

| 측정 항목 | Missed | Total | Coverage |
|----------|--------|-------|----------|
| **Instructions** | 253 | 3,195 | **92%** |
| **Branches** | 13 | 118 | **88%** |
| **Lines** | 50 | 800 | **93%** |
| **Methods** | 35 | 203 | **82%** |
| **Classes** | 0 | 45 | **100%** |

#### 패키지별 커버리지

| 패키지 | Instruction Coverage | Branch Coverage |
|--------|---------------------|-----------------|
| `api.controller` | 100% | - |
| `application.cart` | 96% | 100% |
| `application.coupon` | 96% | 83% |
| `application.order` | 89% | 100% |
| `application.point` | 100% | - |
| `application.product` | 100% | 100% |
| `domain.cart` | 91% | 100% |
| `domain.coupon` | 96% | 100% |
| `domain.order` | 98% | 100% |
| `domain.point` | 97% | 100% |
| `domain.product` | 96% | 100% |
| `domain.user` | 87% | - |
| `infrastructure.repository.*` | 71-99% | 50-100% |

#### JaCoCo 설정

**리포트 생성**: 테스트 실행 시 자동 생성
```bash
./gradlew test
# 리포트 위치: build/reports/jacoco/test/html/index.html
```

### 테스트 구성

| 테스트 유형 | 파일 수 | 커버리지 | 설명 |
|-----------|--------|----------|------|
| **Integration Tests** | 5개 | 5/5 (100%) | Controller 레이어 통합 테스트 |
| **UseCase Tests** | 17개 | 17/17 (100%) | Application 레이어 단위 테스트 |
| **Domain Tests** | 8개 | 8개 핵심 엔티티 | Domain 엔티티 단위 테스트 |
| **총계** | **30개** | **전체 소스 92개** | - |

---

## 동시성 제어 방식

### 1. Application Layer - UseCase Level

#### 1.1 CreateOrderUseCase - 메서드 레벨 Synchronized

```java
public synchronized CreateOrderResponse execute(Long userId, CreateOrderRequest request){}
```

**적용 이유**:
- 주문 생성 과정이 여러 단계의 원자적 작업으로 구성됨
  1. 장바구니 조회
  2. 재고 차감
  3. 쿠폰 사용 처리
  4. 포인트 차감
  5. 장바구니 비우기

**특징**:
- 전체 주문 프로세스를 동기화하여 원자성 보장
- 모든 주문이 순차 처리되어 성능 제약이 있을 수 있음

---

#### 1.2 IssueCouponUseCase - 세밀한 락(Fine-grained Locking)

```java
private final ConcurrentHashMap<Long, Object> couponLocks = new ConcurrentHashMap<>();

public IssueCouponResponse execute(Long userId, Long couponId) {
    Object lock = couponLocks.computeIfAbsent(couponId, k -> new Object());

    synchronized (lock) {
        // 쿠폰 발급 로직
    }
}
```

**적용 이유**:
- 쿠폰별로 독립적인 락 객체를 사용하여 동시성 향상
- 서로 다른 쿠폰에 대한 발급은 동시에 처리 가능

**동작 방식**:
1. `ConcurrentHashMap`으로 쿠폰 ID별 락 객체 관리
2. `computeIfAbsent`로 락 객체 생성 (스레드 안전)
3. 해당 락으로만 동기화 (쿠폰별 격리)

**특징**:
- 쿠폰 A 발급과 쿠폰 B 발급이 동시에 진행 가능
- 같은 쿠폰에 대한 발급만 순차 처리

---

### 2. Domain Layer - Entity Level

#### 2.1 Coupon Entity - 쿠폰 발급 수량 관리

```java
public synchronized boolean canIssue() {
    LocalDateTime now = LocalDateTime.now();
    return now.isAfter(startAt) && now.isBefore(endAt)
           && issuedQuantity < totalQuantity;
}

public synchronized void issue() {
    if (!canIssue()) {
        throw new CouponException(CouponErrorCode.COUPON_UNAVAILABLE);
    }
    issuedQuantity++;
    updateTimestamp();
}
```

**적용 이유**:
- `issuedQuantity` 필드에 대한 읽기-수정-쓰기 연산의 원자성 보장
- 총 발급 수량 초과 방지

**동작 원리**:
1. `canIssue()`: 발급 가능 여부 검증 (시간, 수량)
2. `issue()`: 발급 수량 증가 및 검증
3. 두 메서드 모두 synchronized로 보호

**다층 동시성 제어**:
- UseCase 레벨: 쿠폰별 락 (IssueCouponUseCase)
- Domain 레벨: 엔티티 메서드 락 (Coupon.issue)
- 이중 보호로 안정성 강화

---

#### 2.2 ProductOption Entity - 재고 차감

```java
public void reduceStock(int quantity) {
    if (quantity <= 0) {
        throw new ProductException(ProductErrorCode.INVALID_DEDUCT_QUANTITY);
    }

    if (!hasStock(quantity)) {
        throw new ProductException(ProductErrorCode.INSUFFICIENT_STOCK);
    }

    this.stock -= quantity;
    updateTimestamp();
}
```

**동시성 제어**:
- 메서드 자체는 synchronized 없음
- 상위 레벨(CreateOrderUseCase)의 synchronized로 보호

**설계 결정**:
- 재고 차감은 주문 프로세스의 일부
- 주문 전체가 동기화되므로 추가 락 불필요
- 불필요한 중첩 락 회피

