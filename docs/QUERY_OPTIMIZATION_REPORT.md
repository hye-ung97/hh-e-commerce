# 인기 상품 조회 쿼리 최적화 보고서

> 최근 3일간 판매량 상위 5개 상품 조회 쿼리의 성능 분석 및 개선 과정

## 개요

### 주요 발견사항
| 쿼리 방식 | 실행 시간 | 개선율 |
|----------|----------|--------|
| 기본 JOIN | 5,681ms | - |
| CTE 방식 | 5,330ms | 6.2% ↓ |
| **서브쿼리 방식** | **2,966ms** | **47.8% ↓** |

**핵심 인사이트**: Product 조인 시점을 늦추는 것만으로 약 **50%의 성능 개선** 달성

---

## 테스트 환경

### 데이터베이스 정보
- **DBMS**: MySQL 8.0+ (InnoDB)
- **스토리지 엔진**: InnoDB
- **문자셋**: utf8mb4

### 데이터 규모
```
주문 (order):                   ~744,000건 (최근 3일)
주문 아이템 (order_item):        ~2,230,000건
상품 (product):                 1,000개
상품 옵션 (product_option):      다수
```

### 관련 인덱스
```sql
-- ORDER 테이블
idx_created_at (created_at)                              -- Covering Index ✅
idx_created_at_status (created_at DESC, status)

-- ORDER_ITEM 테이블
idx_order_product_quantity (order_id, product_option_id, quantity)  -- Covering Index ✅

-- PRODUCT_OPTION 테이블
PRIMARY KEY (id)

-- PRODUCT 테이블
PRIMARY KEY (id)
```

---

## 쿼리 #1: 기본 JOIN 방식

### SQL
```sql
SELECT
    p.id as productId,
    p.name as productName,
    p.category as category,
    p.status as status,
    SUM(oi.quantity) as totalSales
FROM order_item oi
JOIN product_option po ON oi.product_option_id = po.id
JOIN product p ON po.product_id = p.id
JOIN `order` o ON oi.order_id = o.id
WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
GROUP BY p.id, p.name, p.category, p.status
ORDER BY totalSales DESC
LIMIT 5;
```

### 실행 계획 (EXPLAIN ANALYZE)
```
-> Limit: 5 row(s)  (actual time=5681..5681 rows=5 loops=1)
    -> Sort: totalSales DESC, limit input to 5 row(s) per chunk  (actual time=5681..5681 rows=5 loops=1)
        -> Table scan on <temporary>  (actual time=5681..5681 rows=1000 loops=1)
            -> Aggregate using temporary table  (actual time=5681..5681 rows=1000 loops=1)
                -> Nested loop inner join  (cost=1.64e+6 rows=1.34e+6) (actual time=0.108..4147 rows=2.23e+6 loops=1)
                    -> Nested loop inner join  (cost=1.17e+6 rows=1.34e+6) (actual time=0.101..3144 rows=2.23e+6 loops=1)
                        -> Nested loop inner join  (cost=704191 rows=1.34e+6) (actual time=0.0933..2232 rows=2.23e+6 loops=1)
                            -> Filter: (o.created_at >= <cache>((now() - interval 3 day)))  (cost=95386 rows=472323) (actual time=0.0636..129 rows=744770 loops=1)
                                -> Covering index range scan on o using idx_created_at  (cost=95386 rows=472323) (actual time=0.0458..91.1 rows=744770 loops=1)
                            -> Covering index lookup on oi using idx_order_product_quantity (order_id=o.id)  (cost=1.01 rows=2.83) (actual time=0.00239..0.00269 rows=3 loops=744770)
                        -> Single-row index lookup on po using PRIMARY (id=oi.product_option_id)  (cost=0.25 rows=1) (actual time=318e-6..334e-6 rows=1 loops=2.23e+6)
                    -> Single-row index lookup on p using PRIMARY (id=po.product_id)  (cost=0.25 rows=1) (actual time=359e-6..375e-6 rows=1 loops=2.23e+6)
```

### 성능 분석

#### ⏱️ 타이밍 분석
| 단계 | 누적 시간 | 처리 건수 | 설명 |
|------|----------|----------|------|
| Order 스캔 | 129ms | 744,770건 | idx_created_at 사용 ✅ |
| OrderItem 조인 | 2,232ms | 2,230,000건 | idx_order_product_quantity 사용 ✅ |
| ProductOption 조인 | 3,144ms | 2,230,000건 | PRIMARY KEY 사용 ✅ |
| **Product 조인** | **4,147ms** | **2,230,000건** | ⚠️ **병목 구간** |
| GROUP BY 집계 | 5,681ms | 1,000개 | 임시 테이블 사용 ⚠️ |
| SORT + LIMIT | 5,681ms | 5개 | 최종 결과 |

#### 🔴 발견된 문제점

**1. 대량의 중간 데이터 처리**
- 223만 건의 order_item을 모두 조인한 후 집계
- 실제 필요한 결과는 5건이지만, 중간에 223만 건 처리
- Product 테이블을 **223만 번** 조인 ← 핵심 병목!

**2. 임시 테이블 사용**
- `Aggregate using temporary table` → 메모리/디스크 사용
- GROUP BY로 1,000개 상품으로 집계

**3. Nested Loop Join의 반복**
- 744,770번의 loop 실행
- 각 루프마다 평균 3개의 order_item 조회

#### ✅ 잘 작동하는 부분
- 모든 인덱스가 적절히 활용됨 (Covering Index 포함)
- 조인 자체는 인덱스를 통해 효율적으로 수행됨

---

## 쿼리 #2: CTE 방식

### SQL
```sql
WITH recent_orders AS (
    SELECT id
    FROM `order`
    WHERE created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
)
SELECT
    p.id as productId,
    p.name as productName,
    p.category as category,
    p.status as status,
    SUM(oi.quantity) as totalSales
FROM order_item oi
INNER JOIN recent_orders ro ON oi.order_id = ro.id
INNER JOIN product_option po ON oi.product_option_id = po.id
INNER JOIN product p ON po.product_id = p.id
GROUP BY p.id, p.name, p.category, p.status
ORDER BY totalSales DESC
LIMIT 5;
```

### 실행 계획 (EXPLAIN ANALYZE)
```
-> Limit: 5 row(s)  (actual time=5330..5330 rows=5 loops=1)
    -> Sort: totalSales DESC, limit input to 5 row(s) per chunk  (actual time=5330..5330 rows=5 loops=1)
        -> Table scan on <temporary>  (actual time=5329..5329 rows=1000 loops=1)
            -> Aggregate using temporary table  (actual time=5329..5329 rows=1000 loops=1)
                -> Nested loop inner join  (cost=1.64e+6 rows=1.34e+6) (actual time=0.0923..3834 rows=2.23e+6 loops=1)
                    -> Nested loop inner join  (cost=1.17e+6 rows=1.34e+6) (actual time=0.0845..2847 rows=2.23e+6 loops=1)
                        -> Nested loop inner join  (cost=704191 rows=1.34e+6) (actual time=0.0735..2036 rows=2.23e+6 loops=1)
                            -> Filter: (`order`.created_at >= <cache>((now() - interval 3 day)))  (cost=95386 rows=472323) (actual time=0.0426..119 rows=744225 loops=1)
                                -> Covering index range scan on order using idx_created_at  (cost=95386 rows=472323) (actual time=0.0342..88.3 rows=744225 loops=1)
                            -> Covering index lookup on oi using idx_order_product_quantity (order_id=`order`.id)  (cost=1.01 rows=2.83) (actual time=0.00215..0.00244 rows=3 loops=744225)
                        -> Single-row index lookup on po using PRIMARY (id=oi.product_option_id)  (cost=0.25 rows=1) (actual time=274e-6..289e-6 rows=1 loops=2.23e+6)
                    -> Single-row index lookup on p using PRIMARY (id=po.product_id)  (cost=0.25 rows=1) (actual time=351e-6..368e-6 rows=1 loops=2.23e+6)
```

### 성능 분석

#### ⏱️ 성능 지표
- **실행 시간**: 5,330ms
- **처리 주문 수**: 744,225건
- **처리 아이템 수**: 2,230,000건
- **개선율**: 6.2% (5,681ms → 5,330ms)

#### ❌ CTE가 효과가 없는 이유

**1. MySQL 옵티마이저의 CTE 병합**
```
CTE 작성:
WITH recent_orders AS (SELECT id FROM order WHERE ...)

실제 실행:
ORDER 테이블을 직접 스캔 (CTE 무시됨)
```

**2. CTE 구체화(Materialization) 조건 미충족**
- 단순 필터만 있는 CTE는 인라인으로 병합됨
- 복잡한 집계나 재사용이 있어야 구체화됨
- 이 경우 `WHERE created_at >= ...`로 자동 변환됨

**3. 실행 계획이 기본 쿼리와 동일**
- 여전히 Product를 223만 번 조인
- 임시 테이블 사용
- Nested loop의 비효율 그대로 유지

#### 💡 성능 차이의 실제 원인
- 약 6%의 개선은 **쿼리 최적화가 아님**
- 실행 시점의 캐시 상태, CPU 상태 차이
- 데이터 약간의 차이 (744,770 vs 744,225)

---

## 쿼리 #3: 서브쿼리 방식 (최적화)

### SQL
```sql
SELECT
    p.id, p.name, p.category, p.status,
    sales.totalSales
FROM (
    SELECT po.product_id, SUM(oi.quantity) as totalSales
    FROM order_item oi
    INNER JOIN product_option po ON oi.product_option_id = po.id
    WHERE oi.order_id IN (
        SELECT id FROM `order`
        WHERE created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
    )
    GROUP BY po.product_id
    ORDER BY totalSales DESC
    LIMIT 5
) sales
JOIN product p ON p.id = sales.product_id;
```

### 실행 계획 (EXPLAIN ANALYZE)
```
-> Nested loop inner join  (cost=3.75 rows=0) (actual time=2966..2966 rows=5 loops=1)
    -> Table scan on sales  (cost=2.5..2.5 rows=0) (actual time=2966..2966 rows=5 loops=1)
        -> Materialize  (cost=0..0 rows=0) (actual time=2966..2966 rows=5 loops=1)
            -> Limit: 5 row(s)  (actual time=2966..2966 rows=5 loops=1)
                -> Sort: totalSales DESC, limit input to 5 row(s) per chunk  (actual time=2966..2966 rows=5 loops=1)
                    -> Table scan on <temporary>  (actual time=2965..2965 rows=1000 loops=1)
                        -> Aggregate using temporary table  (actual time=2965..2965 rows=1000 loops=1)
                            -> Nested loop inner join  (cost=1.17e+6 rows=1.34e+6) (actual time=0.0501..2621 rows=2.23e+6 loops=1)
                                -> Nested loop inner join  (cost=704191 rows=1.34e+6) (actual time=0.044..1850 rows=2.23e+6 loops=1)
                                    -> Filter: (`order`.created_at >= <cache>((now() - interval 3 day)))  (cost=95386 rows=472323) (actual time=0.0258..110 rows=743699 loops=1)
                                        -> Covering index range scan on order using idx_created_at  (cost=95386 rows=472323) (actual time=0.0201..81.8 rows=743699 loops=1)
                                    -> Covering index lookup on oi using idx_order_product_quantity (order_id=`order`.id)  (cost=1.01 rows=2.83) (actual time=0.00194..0.00221 rows=3 loops=743699)
                                -> Single-row index lookup on po using PRIMARY (id=oi.product_option_id)  (cost=0.25 rows=1) (actual time=258e-6..273e-6 rows=1 loops=2.23e+6)
    -> Single-row index lookup on p using PRIMARY (id=sales.product_id)  (cost=0.27 rows=1) (actual time=0.0071..0.00713 rows=1 loops=5)
```

### 성능 분석

#### ⏱️ 타이밍 분석
| 단계 | 누적 시간 | 단계 소요 | 처리 건수 | 설명 |
|------|----------|----------|----------|------|
| Order 스캔 | 110ms | 110ms | 743,699건 | idx_created_at 사용 ✅ |
| OrderItem 조인 | 1,850ms | 1,740ms | 2,230,000건 | idx_order_product_quantity 사용 ✅ |
| ProductOption 조인 | 2,621ms | 771ms | 2,230,000건 | PRIMARY KEY 사용 ✅ |
| GROUP BY 집계 | 2,965ms | 344ms | 1,000개 | 임시 테이블 사용 |
| SORT + LIMIT | 2,966ms | 1ms | 5개 | TOP 5 선택 |
| **Product 조인** | 2,966ms | **0ms** | **5건** | ✅ **핵심 개선!** |

#### 🚀 핵심 차이점

**1. 실행 순서의 변화**

**기본 쿼리 (느림)**:
```
order → order_item → product_option → product
  ↓         ↓             ↓             ↓
744K     2.23M         2.23M         2.23M  ← Product 223만번 조인! ❌
  ↓
GROUP BY (1000개)
  ↓
SORT + LIMIT 5
```

**서브쿼리 방식 (빠름)**:
```
order → order_item → product_option
  ↓         ↓             ↓
744K     2.23M         2.23M
  ↓
GROUP BY (1000개)
  ↓
SORT + LIMIT 5  ← 여기서 5개만 선택!
  ↓
product 조인 (5건만!)  ✅ 핵심!
```

**2. Product 조인 시점**

| 방식 | Product 조인 시점 | 조인 횟수 | 영향 |
|------|------------------|----------|------|
| 기본 쿼리 | GROUP BY 전 | 2,230,000번 | ❌ 비효율 |
| **서브쿼리** | **LIMIT 5 후** | **5번** | ✅ **99.9998% 감소** |

**3. Materialize 효과**
```
-> Materialize  (actual time=2966..2966 rows=5 loops=1)
```
- 서브쿼리 결과(5건)를 임시로 구체화
- 외부 쿼리에서 재사용 가능
- 메모리에 5건만 저장 → 매우 효율적

**4. Product 조인 최적화**
```
-> Single-row index lookup on p using PRIMARY (id=sales.product_id)
   (cost=0.27 rows=1) (actual time=0.0071..0.00713 rows=1 loops=5)
```
- **loops=5** ← 5번만 실행!
- 각 조인당 0.007ms (마이크로초 수준)
- PRIMARY KEY 사용으로 즉시 조회

#### ✅ 왜 빠른가?

**비용 절감**
```
기본 쿼리:
2,230,000 rows × (po lookup + p lookup) = 엄청난 I/O

서브쿼리:
2,230,000 rows × po lookup + 5 rows × p lookup = 최소 I/O
```

**I/O 비교**
- 기본 쿼리: Product 테이블 접근 **2,230,000번**
- 서브쿼리: Product 테이블 접근 **5번**
- **감소율: 99.9998%**

---

## 성능 비교 요약

### 📊 전체 비교표

| 항목 | 기본 JOIN | CTE 방식 | 서브쿼리 방식 | 개선율 |
|------|----------|----------|-------------|--------|
| **실행 시간** | 5,681ms | 5,330ms | **2,966ms** | **47.8% ↓** |
| **처리 주문** | 744,770건 | 744,225건 | 743,699건 | 유사 |
| **처리 아이템** | 2,230,000건 | 2,230,000건 | 2,230,000건 | 동일 |
| **Product 조인** | 2,230,000번 | 2,230,000번 | **5번** | **99.9998% ↓** |
| **Materialize** | ❌ | ❌ | ✅ | - |

