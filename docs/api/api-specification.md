# API 명세서

## API 목록

### 1. 상품 관리 API

#### 1.1 상품 목록 조회
- **Endpoint**: `GET /api/products`
- **Description**: 전체 상품 목록을 조회합니다.

#### 1.2 상품 상세 조회
- **Endpoint**: `GET /api/products/{productId}`
- **Description**: 특정 상품의 상세 정보를 조회합니다 (가격, 재고, 옵션 포함).

#### 1.3 인기 상품 조회
- **Endpoint**: `GET /api/products/popular`
- **Description**: 최근 3일간 가장 많이 판매된 상품 Top 5를 조회합니다.

### 2. 장바구니 API

#### 2.1 장바구니 조회
- **Endpoint**: `GET /api/carts`
- **Description**: 사용자의 장바구니 목록을 조회합니다.

#### 2.2 장바구니 상품 추가
- **Endpoint**: `POST /api/carts`
- **Description**: 장바구니에 상품을 추가합니다.

#### 2.3 장바구니 상품 수량 변경
- **Endpoint**: `PATCH /api/carts/{cartId}`
- **Description**: 장바구니 상품의 수량을 변경합니다.

#### 2.4 장바구니 상품 삭제
- **Endpoint**: `DELETE /api/carts/{cartId}`
- **Description**: 장바구니에서 상품을 삭제합니다.

### 3. 주문/결제 API

#### 3.1 주문 생성
- **Endpoint**: `POST /api/orders`
- **Description**: 장바구니 상품으로 주문을 생성하고 결제를 진행합니다 (재고 확인 및 차감, 쿠폰 적용).

#### 3.2 주문 목록 조회
- **Endpoint**: `GET /api/orders`
- **Description**: 사용자의 주문 내역을 조회합니다.

#### 3.3 주문 상세 조회
- **Endpoint**: `GET /api/orders/{orderId}`
- **Description**: 특정 주문의 상세 정보를 조회합니다.

### 4. 쿠폰 API

#### 4.1 발급 가능한 쿠폰 목록 조회
- **Endpoint**: `GET /api/coupons`
- **Description**: 현재 발급 가능한 쿠폰 목록을 조회합니다.

#### 4.2 쿠폰 발급
- **Endpoint**: `POST /api/coupons/{couponId}/issue`
- **Description**: 선착순으로 쿠폰을 발급받습니다.

#### 4.3 보유 쿠폰 조회
- **Endpoint**: `GET /api/users/coupons`
- **Description**: 사용자가 보유한 쿠폰 목록을 조회합니다.

#### 4.4 사용 가능한 쿠폰 조회
- **Endpoint**: `GET /api/users/coupons/available`
- **Description**: 현재 주문에 사용 가능한 쿠폰 목록을 조회합니다.

### 5. 포인트 API

#### 5.1 포인트 조회
- **Endpoint**: `GET /api/point`
- **Description**: 사용자의 현재 포인트를 조회합니다.

#### 5.2 포인트 충전
- **Endpoint**: `POST /api/point/charge`
- **Description**: 사용자의 포인트를 충전합니다.


---

## 시퀀스 다이어그램

### 1. 상품 관리 API

#### 1.1 상품 목록 조회

```mermaid
sequenceDiagram
    autonumber
    participant C as ProductController
    participant S as ProductService
    participant PR as ProductRepository
    participant DB as Database

    C->>S: 상품 목록 조회 요청
    activate S

    Note over S: 필터 조건 검증 및 처리

    S->>PR: 상품 목록 필터 조회
    activate PR
    PR->>DB: 상품 데이터 조회
    DB-->>PR: 상품 목록 + 총 개수
    deactivate PR

    alt Products is empty
        S-->>C: PageResponse<ProductResponse> (empty)
    end

    Note over S: 응답 데이터 매핑

    S-->>C: PageResponse<ProductResponse>
    deactivate S
```

#### 1.2 상품 상세 조회

```mermaid
sequenceDiagram
    autonumber
    participant C as ProductController
    participant S as ProductService
    participant PR as ProductRepository
    participant POR as ProductOptionRepository
    participant DB as Database

    C->>S: 상품 상세 조회 요청
    activate S

    S->>PR: 상품 정보 조회
    activate PR
    PR->>DB: 상품 데이터 조회
    DB-->>PR: 상품 정보
    deactivate PR

    alt Product not found
        S-->>C: throw ProductNotFoundException
    end

    alt Product status is not ACTIVE
        S-->>C: throw ProductNotAvailableException
    end

    S->>POR: 상품 옵션 조회
    activate POR
    POR->>DB: 옵션 데이터 조회
    DB-->>POR: 옵션 목록
    deactivate POR

    Note over S: 상품 정보 + 옵션 매핑

    S-->>C: ProductDetailResponse
    deactivate S
```

#### 1.3 인기 상품 조회

```mermaid
sequenceDiagram
    autonumber
    participant C as ProductController
    participant S as ProductService
    participant OIR as OrderItemRepository
    participant PR as ProductRepository
    participant POR as ProductOptionRepository
    participant DB as Database

    C->>S: 인기 상품 조회 요청
    activate S

    Note over S: 최근 3일 날짜 계산

    S->>OIR: 기간별 판매량 상위 상품 조회
    activate OIR
    OIR->>DB: 판매량 집계 데이터 조회
    DB-->>OIR: 판매량 정보
    deactivate OIR

    alt No sales data
        S-->>C: List<PopularProductResponse> (empty)
    end

    Note over S: 상품 ID 추출

    S->>PR: 상품 정보 조회
    activate PR
    PR->>DB: 상품 데이터 조회
    DB-->>PR: 상품 목록
    deactivate PR

    S->>POR: 상품 옵션 조회
    activate POR
    POR->>DB: 옵션 데이터 조회
    DB-->>POR: 옵션 목록
    deactivate POR

    Note over S: 상품 정보 + 옵션 + 판매량 매핑<br/>판매량 순으로 정렬

    S-->>C: List<PopularProductResponse>
    deactivate S
```

### 2. 장바구니 API

#### 2.1 장바구니 조회

```mermaid
sequenceDiagram
    autonumber
    participant C as CartController
    participant S as CartService
    participant CR as CartRepository
    participant POR as ProductOptionRepository
    participant PR as ProductRepository
    participant DB as Database

    C->>S: 장바구니 조회 요청
    activate S

    S->>CR: 장바구니 목록 조회
    activate CR
    CR->>DB: 장바구니 데이터 조회
    DB-->>CR: 장바구니 목록
    deactivate CR

    alt Cart is empty
        S-->>C: List<CartResponse> (empty)
    end

    Note over S: 상품 옵션 ID 추출

    S->>POR: 상품 옵션 조회
    activate POR
    POR->>DB: 옵션 데이터 조회
    DB-->>POR: 옵션 목록
    deactivate POR

    Note over S: 상품 ID 추출

    S->>PR: 상품 정보 조회
    activate PR
    PR->>DB: 상품 데이터 조회
    DB-->>PR: 상품 목록
    deactivate PR

    Note over S: 장바구니 + 상품 + 옵션 매핑

    S-->>C: List<CartResponse>
    deactivate S
```

#### 2.2 장바구니 상품 추가

```mermaid
sequenceDiagram
    autonumber
    participant C as CartController
    participant S as CartService
    participant POR as ProductOptionRepository
    participant CR as CartRepository
    participant DB as Database

    C->>S: 장바구니 상품 추가 요청
    activate S

    Note over S: 수량 유효성 검증

    S->>POR: 상품 옵션 조회
    activate POR
    POR->>DB: 옵션 데이터 조회
    DB-->>POR: 옵션 정보
    deactivate POR

    alt ProductOption not found
        S-->>C: throw ProductOptionNotFoundException
    end

    alt Stock is insufficient
        S-->>C: throw InsufficientStockException
    end

    S->>CR: 기존 장바구니 항목 확인
    activate CR
    CR->>DB: 장바구니 데이터 조회
    DB-->>CR: 장바구니 정보
    deactivate CR

    alt Cart already exists
        Note over S: 기존 수량 + 요청 수량
        S->>CR: 수량 업데이트
        activate CR
        CR->>DB: 장바구니 수량 변경
        DB-->>CR: 업데이트 결과
        deactivate CR
    else Cart not exists
        Note over S: 새 장바구니 항목 생성
        S->>CR: 장바구니 저장
        activate CR
        CR->>DB: 장바구니 데이터 삽입
        DB-->>CR: 저장된 장바구니
        deactivate CR
    end

    S-->>C: CartResponse
    deactivate S
```

#### 2.3 장바구니 상품 수량 변경

```mermaid
sequenceDiagram
    autonumber
    participant C as CartController
    participant S as CartService
    participant CR as CartRepository
    participant POR as ProductOptionRepository
    participant DB as Database

    C->>S: 장바구니 수량 변경 요청
    activate S

    Note over S: 수량 유효성 검증

    S->>CR: 장바구니 항목 조회
    activate CR
    CR->>DB: 장바구니 데이터 조회
    DB-->>CR: 장바구니 정보
    deactivate CR

    alt Cart not found
        S-->>C: throw CartNotFoundException
    end

    S->>POR: 상품 옵션 조회
    activate POR
    POR->>DB: 옵션 데이터 조회
    DB-->>POR: 옵션 정보
    deactivate POR

    alt Stock is insufficient
        S-->>C: throw InsufficientStockException
    end

    S->>CR: 수량 업데이트
    activate CR
    CR->>DB: 장바구니 수량 변경
    DB-->>CR: 업데이트 결과
    deactivate CR

    S-->>C: CartResponse
    deactivate S
```

#### 2.4 장바구니 상품 삭제

```mermaid
sequenceDiagram
    autonumber
    participant C as CartController
    participant S as CartService
    participant CR as CartRepository
    participant DB as Database

    C->>S: 장바구니 상품 삭제 요청
    activate S

    S->>CR: 장바구니 항목 조회
    activate CR
    CR->>DB: 장바구니 데이터 조회
    DB-->>CR: 장바구니 정보
    deactivate CR

    alt Cart not found
        S-->>C: throw CartNotFoundException
    end

    S->>CR: 장바구니 삭제
    activate CR
    CR->>DB: 장바구니 데이터 삭제
    DB-->>CR: 삭제 결과
    deactivate CR

    S-->>C: success
    deactivate S
```

### 3. 주문/결제 API

#### 3.1 주문 생성

```mermaid
sequenceDiagram
    autonumber
    participant C as OrderController
    participant S as OrderService
    participant CR as CartRepository
    participant POR as ProductOptionRepository
    participant UCR as UserCouponRepository
    participant OR as OrderRepository
    participant OIR as OrderItemRepository
    participant PayR as PaymentRepository
    participant PCR as PaymentCouponRepository
    participant PR as PointRepository
    participant ES as ExternalService
    participant DB as Database

    C->>S: 주문 생성 요청
    activate S

    Note over S: 트랜잭션 시작

    S->>CR: 장바구니 조회
    activate CR
    CR->>DB: 장바구니 데이터 조회
    DB-->>CR: 장바구니 목록
    deactivate CR

    alt Cart is empty
        S-->>C: throw EmptyCartException
    end

    Note over S: 상품 옵션 ID 추출

    S->>POR: 상품 옵션 조회 (락)
    activate POR
    POR->>DB: 옵션 데이터 조회 및 잠금
    DB-->>POR: 옵션 목록
    deactivate POR

    Note over S: 재고 확인 (비관적 락)

    alt Stock is insufficient
        S-->>C: throw InsufficientStockException
    end

    Note over S: 총 주문 금액 계산

    opt Coupon is provided
        S->>UCR: 사용자 쿠폰 조회 (락)
        activate UCR
        UCR->>DB: 쿠폰 데이터 조회 및 잠금
        DB-->>UCR: 쿠폰 정보
        deactivate UCR

        alt Coupon not available
            S-->>C: throw CouponNotAvailableException
        end

        Note over S: 쿠폰 할인 금액 계산
    end

    Note over S: 최종 결제 금액 계산

    S->>PR: 포인트 조회 (락)
    activate PR
    PR->>DB: 포인트 데이터 조회 및 잠금
    DB-->>PR: 포인트 정보
    deactivate PR

    alt Point is insufficient
        S-->>C: throw InsufficientPointException
    end

    S->>OR: 주문 생성
    activate OR
    OR->>DB: 주문 데이터 삽입
    DB-->>OR: 주문 정보
    deactivate OR

    loop For each cart item
        S->>OIR: 주문 항목 생성
        activate OIR
        OIR->>DB: 주문 항목 데이터 삽입
        DB-->>OIR: 주문 항목
        deactivate OIR

        S->>POR: 재고 차감
        activate POR
        POR->>DB: 재고 수량 감소
        DB-->>POR: 업데이트 결과
        deactivate POR
    end

    S->>PayR: 결제 정보 생성
    activate PayR
    PayR->>DB: 결제 데이터 삽입
    DB-->>PayR: 결제 정보
    deactivate PayR

    opt Coupon is used
        S->>PCR: 결제 쿠폰 정보 생성
        activate PCR
        PCR->>DB: 결제 쿠폰 데이터 삽입
        DB-->>PCR: 결제 쿠폰 정보
        deactivate PCR

        S->>UCR: 쿠폰 사용 처리
        activate UCR
        UCR->>DB: 쿠폰 상태 변경
        DB-->>UCR: 업데이트 결과
        deactivate UCR
    end

    S->>PR: 포인트 차감
    activate PR
    PR->>DB: 포인트 감소
    DB-->>PR: 업데이트 결과
    deactivate PR

    S->>CR: 장바구니 비우기
    activate CR
    CR->>DB: 장바구니 데이터 삭제
    DB-->>CR: 삭제 결과
    deactivate CR

    Note over S: 트랜잭션 커밋

    Note over S: 외부 시스템 전송 (비동기)

    S->>ES: 주문 데이터 전송
    activate ES
    Note over ES: 비동기 처리<br/>(실패해도 주문은 정상 처리)
    deactivate ES

    S-->>C: OrderResponse
    deactivate S
```

#### 3.2 주문 목록 조회

```mermaid
sequenceDiagram
    autonumber
    participant C as OrderController
    participant S as OrderService
    participant OR as OrderRepository
    participant DB as Database

    C->>S: 주문 목록 조회 요청
    activate S

    S->>OR: 주문 목록 조회
    activate OR
    OR->>DB: 주문 데이터 조회
    DB-->>OR: 주문 목록 + 총 개수
    deactivate OR

    alt Orders is empty
        S-->>C: PageResponse<OrderResponse> (empty)
    end

    Note over S: 응답 데이터 매핑

    S-->>C: PageResponse<OrderResponse>
    deactivate S
```

#### 3.3 주문 상세 조회

```mermaid
sequenceDiagram
    autonumber
    participant C as OrderController
    participant S as OrderService
    participant OR as OrderRepository
    participant OIR as OrderItemRepository
    participant POR as ProductOptionRepository
    participant PR as ProductRepository
    participant PayR as PaymentRepository
    participant DB as Database

    C->>S: 주문 상세 조회 요청
    activate S

    S->>OR: 주문 정보 조회
    activate OR
    OR->>DB: 주문 데이터 조회
    DB-->>OR: 주문 정보
    deactivate OR

    alt Order not found
        S-->>C: throw OrderNotFoundException
    end

    S->>OIR: 주문 항목 조회
    activate OIR
    OIR->>DB: 주문 항목 데이터 조회
    DB-->>OIR: 주문 항목 목록
    deactivate OIR

    Note over S: 상품 옵션 ID 추출

    S->>POR: 상품 옵션 조회
    activate POR
    POR->>DB: 옵션 데이터 조회
    DB-->>POR: 옵션 목록
    deactivate POR

    Note over S: 상품 ID 추출

    S->>PR: 상품 정보 조회
    activate PR
    PR->>DB: 상품 데이터 조회
    DB-->>PR: 상품 목록
    deactivate PR

    S->>PayR: 결제 정보 조회
    activate PayR
    PayR->>DB: 결제 데이터 조회
    DB-->>PayR: 결제 정보
    deactivate PayR

    Note over S: 주문 + 주문항목 + 결제정보 매핑

    S-->>C: OrderDetailResponse
    deactivate S
```


### 4. 쿠폰 API

#### 4.1 발급 가능한 쿠폰 목록 조회

```mermaid
sequenceDiagram
    autonumber
    participant C as CouponController
    participant S as CouponService
    participant CR as CouponRepository
    participant DB as Database

    C->>S: 발급 가능 쿠폰 조회 요청
    activate S

    Note over S: 현재 시각 기준<br/>발급 가능 쿠폰 조회

    S->>CR: 발급 가능 쿠폰 조회
    activate CR
    CR->>DB: 쿠폰 데이터 조회
    DB-->>CR: 쿠폰 목록
    deactivate CR

    alt Coupons is empty
        S-->>C: List<CouponResponse> (empty)
    end

    Note over S: 남은 수량 계산

    S-->>C: List<CouponResponse>
    deactivate S
```

#### 4.2 쿠폰 발급

```mermaid
sequenceDiagram
    autonumber
    participant C as CouponController
    participant S as CouponService
    participant CR as CouponRepository
    participant UCR as UserCouponRepository
    participant DB as Database

    C->>S: 쿠폰 발급 요청
    activate S

    Note over S: 트랜잭션 시작

    S->>CR: 쿠폰 조회 (락)
    activate CR
    CR->>DB: 쿠폰 데이터 조회 및 잠금
    DB-->>CR: 쿠폰 정보
    deactivate CR

    alt Coupon not found
        S-->>C: throw CouponNotFoundException
    end

    Note over S: 쿠폰 발급 가능 여부 검증

    alt Coupon not in valid period
        S-->>C: throw CouponNotInPeriodException
    end

    alt Coupon sold out
        S-->>C: throw CouponSoldOutException
    end

    S->>UCR: 중복 발급 확인
    activate UCR
    UCR->>DB: 사용자 쿠폰 존재 여부 확인
    DB-->>UCR: 존재 여부
    deactivate UCR

    alt User already has this coupon
        S-->>C: throw CouponAlreadyIssuedException
    end

    S->>UCR: 사용자 쿠폰 생성
    activate UCR
    UCR->>DB: 사용자 쿠폰 데이터 삽입
    DB-->>UCR: 사용자 쿠폰 정보
    deactivate UCR

    S->>CR: 발급 수량 증가
    activate CR
    CR->>DB: 발급 수량 증가
    DB-->>CR: 업데이트 결과
    deactivate CR

    Note over S: 트랜잭션 커밋

    S-->>C: UserCouponResponse
    deactivate S
```

#### 4.3 보유 쿠폰 조회

```mermaid
sequenceDiagram
    autonumber
    participant C as CouponController
    participant S as CouponService
    participant UCR as UserCouponRepository
    participant CR as CouponRepository
    participant DB as Database

    C->>S: 보유 쿠폰 조회 요청
    activate S

    S->>UCR: 사용자 쿠폰 조회
    activate UCR
    UCR->>DB: 사용자 쿠폰 데이터 조회
    DB-->>UCR: 사용자 쿠폰 목록
    deactivate UCR

    alt UserCoupons is empty
        S-->>C: List<UserCouponResponse> (empty)
    end

    Note over S: 쿠폰 ID 추출

    S->>CR: 쿠폰 정보 조회
    activate CR
    CR->>DB: 쿠폰 데이터 조회
    DB-->>CR: 쿠폰 목록
    deactivate CR

    Note over S: 사용자 쿠폰 + 쿠폰 정보 매핑

    S-->>C: List<UserCouponResponse>
    deactivate S
```

#### 4.4 사용 가능한 쿠폰 조회

```mermaid
sequenceDiagram
    autonumber
    participant C as CouponController
    participant S as CouponService
    participant UCR as UserCouponRepository
    participant CR as CouponRepository
    participant DB as Database

    C->>S: 사용 가능 쿠폰 조회 요청
    activate S

    Note over S: 현재 시각 기준

    S->>UCR: 미만료 사용자 쿠폰 조회
    activate UCR
    UCR->>DB: 사용자 쿠폰 데이터 조회
    DB-->>UCR: 사용자 쿠폰 목록
    deactivate UCR

    alt UserCoupons is empty
        S-->>C: List<UserCouponResponse> (empty)
    end

    Note over S: 쿠폰 ID 추출

    S->>CR: 쿠폰 정보 조회
    activate CR
    CR->>DB: 쿠폰 데이터 조회
    DB-->>CR: 쿠폰 목록
    deactivate CR

    Note over S: 쿠폰 사용 가능 여부 필터링<br/>(최소 주문 금액 충족)

    Note over S: 사용자 쿠폰 + 쿠폰 정보 매핑<br/>+ 할인 금액 계산

    S-->>C: List<UserCouponResponse>
    deactivate S
```


### 5. 포인트 API

#### 5.1 포인트 조회

```mermaid
sequenceDiagram
    autonumber
    participant C as PointController
    participant S as PointService
    participant PR as PointRepository
    participant DB as Database

    C->>S: 포인트 조회 요청
    activate S

    S->>PR: 포인트 조회
    activate PR
    PR->>DB: 포인트 데이터 조회
    DB-->>PR: 포인트 정보
    deactivate PR

    alt Point not found
        S-->>C: throw PointNotFoundException
    end

    S-->>C: PointResponse
    deactivate S
```

#### 5.2 포인트 충전

```mermaid
sequenceDiagram
    autonumber
    participant C as PointController
    participant S as PointService
    participant PR as PointRepository
    participant DB as Database

    C->>S: 포인트 충전 요청
    activate S

    Note over S: 충전 금액 유효성 검증

    Note over S: 트랜잭션 시작

    S->>PR: 포인트 조회 (락)
    activate PR
    PR->>DB: 포인트 데이터 조회 및 잠금
    DB-->>PR: 포인트 정보
    deactivate PR

    alt Point not found
        Note over S: 새로운 포인트 생성
        S->>PR: 포인트 생성
        activate PR
        PR->>DB: 포인트 데이터 삽입
        DB-->>PR: 포인트 정보
        deactivate PR
    else Point exists
        Note over S: 기존 포인트에 충전
        S->>PR: 포인트 증가
        activate PR
        PR->>DB: 포인트 증가
        DB-->>PR: 업데이트 결과
        deactivate PR
    end

    Note over S: 트랜잭션 커밋

    S-->>C: PointResponse
    deactivate S
```

