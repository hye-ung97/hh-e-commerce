package org.hhplus.hhecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hhplus.hhecommerce.dto.order.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Tag(name = "Order", description = "주문 관리 API")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final AtomicLong ORDER_ID_GENERATOR = new AtomicLong(1);
    private static final Map<Long, Map<String, Object>> ORDERS = new ConcurrentHashMap<>();
    private static final Map<Long, List<Map<String, Object>>> ORDER_ITEMS = new ConcurrentHashMap<>();
    private static final Map<Long, Map<String, Object>> PAYMENTS = new ConcurrentHashMap<>();

    // 테스트용 상품 옵션 데이터
    private static final Map<Long, Map<String, Object>> PRODUCT_OPTIONS = Map.of(
        1L, Map.of("id", 1L, "productId", 1L, "productName", "노트북", "optionName", "색상: 실버, 용량: 256GB", "price", 1500000, "stock", 5),
        2L, Map.of("id", 2L, "productId", 1L, "productName", "노트북", "optionName", "색상: 실버, 용량: 512GB", "price", 1700000, "stock", 5),
        3L, Map.of("id", 3L, "productId", 2L, "productName", "키보드", "optionName", "색상: 블랙", "price", 120000, "stock", 25),
        4L, Map.of("id", 4L, "productId", 2L, "productName", "키보드", "optionName", "색상: 화이트", "price", 120000, "stock", 25),
        5L, Map.of("id", 5L, "productId", 3L, "productName", "마우스", "optionName", "색상: 블랙", "price", 50000, "stock", 50)
    );

    // 테스트용 쿠폰 데이터
    private static final Map<Long, Map<String, Object>> USER_COUPONS = Map.of(
        1L, Map.of("id", 1L, "userId", 1L, "couponId", 1L, "couponName", "신규가입 10% 할인", "discountType", "PERCENTAGE", "discountValue", 10, "minOrderAmount", 100000),
        2L, Map.of("id", 2L, "userId", 1L, "couponId", 2L, "couponName", "5만원 할인쿠폰", "discountType", "FIXED", "discountValue", 50000, "minOrderAmount", 200000)
    );

    @Operation(summary = "주문 생성", description = "상품을 주문하고 결제를 처리합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "주문 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CreateOrderResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "재고 부족, 쿠폰 사용 불가, 또는 잘못된 요청")
    })
    @PostMapping
    public CreateOrderResponse createOrder(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId,
        @RequestBody Map<String, Object> request
    ) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");
        Long userCouponId = request.containsKey("userCouponId") ?
            ((Number) request.get("userCouponId")).longValue() : null;

        if (items == null || items.isEmpty()) {
            throw new RuntimeException("Order items cannot be empty");
        }

        // 주문 항목 검증 및 금액 계산
        List<Map<String, Object>> orderItems = new ArrayList<>();
        List<CreateOrderResponse.OrderItemInfo> orderItemInfos = new ArrayList<>();
        int totalAmount = 0;

        for (Map<String, Object> item : items) {
            Long productOptionId = ((Number) item.get("productOptionId")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();

            Map<String, Object> option = PRODUCT_OPTIONS.get(productOptionId);
            if (option == null) {
                throw new RuntimeException("Product option not found: " + productOptionId);
            }

            int stock = (Integer) option.get("stock");
            if (stock < quantity) {
                throw new RuntimeException("Insufficient stock for " + option.get("productName") + ". Available: " + stock);
            }

            int price = (Integer) option.get("price");
            int subtotal = price * quantity;
            totalAmount += subtotal;

            Map<String, Object> orderItem = new HashMap<>();
            orderItem.put("productOptionId", productOptionId);
            orderItem.put("productId", option.get("productId"));
            orderItem.put("productName", option.get("productName"));
            orderItem.put("optionName", option.get("optionName"));
            orderItem.put("quantity", quantity);
            orderItem.put("unitPrice", price);
            orderItem.put("subtotal", subtotal);
            orderItem.put("status", "ORDERED");
            orderItems.add(orderItem);

            orderItemInfos.add(CreateOrderResponse.OrderItemInfo.builder()
                .productName((String) option.get("productName"))
                .optionName((String) option.get("optionName"))
                .price(price)
                .quantity(quantity)
                .totalPrice(subtotal)
                .build());
        }

        // 쿠폰 할인 계산
        int discountAmount = 0;
        Map<String, Object> usedCoupon = null;

        if (userCouponId != null) {
            usedCoupon = USER_COUPONS.get(userCouponId);
            if (usedCoupon == null || !userId.equals(usedCoupon.get("userId"))) {
                throw new RuntimeException("Coupon not available");
            }

            int minOrderAmount = (Integer) usedCoupon.get("minOrderAmount");
            if (totalAmount < minOrderAmount) {
                throw new RuntimeException("Minimum order amount not met. Required: " + minOrderAmount);
            }

            String discountType = (String) usedCoupon.get("discountType");
            int discountValue = (Integer) usedCoupon.get("discountValue");

            if ("PERCENTAGE".equals(discountType)) {
                discountAmount = totalAmount * discountValue / 100;
            } else if ("FIXED".equals(discountType)) {
                discountAmount = discountValue;
            }
        }

        int finalAmount = totalAmount - discountAmount;
        LocalDateTime now = LocalDateTime.now();

        // 주문 생성
        Long orderId = ORDER_ID_GENERATOR.getAndIncrement();
        Map<String, Object> order = new HashMap<>();
        order.put("id", orderId);
        order.put("userId", userId);
        order.put("totalAmount", totalAmount);
        order.put("discountAmount", discountAmount);
        order.put("finalAmount", finalAmount);
        order.put("status", "ORDERED");
        order.put("createdAt", now);

        ORDERS.put(orderId, order);
        ORDER_ITEMS.put(orderId, orderItems);

        // 결제 정보 생성
        Map<String, Object> payment = new HashMap<>();
        payment.put("id", orderId);
        payment.put("orderId", orderId);
        payment.put("amount", finalAmount);
        payment.put("method", "POINT");
        payment.put("status", "COMPLETED");
        payment.put("createdAt", now);

        if (usedCoupon != null) {
            payment.put("couponId", usedCoupon.get("couponId"));
            payment.put("couponName", usedCoupon.get("couponName"));
            payment.put("couponDiscount", discountAmount);
        }

        PAYMENTS.put(orderId, payment);

        return CreateOrderResponse.builder()
            .id(orderId)
            .userId(userId)
            .status("ORDERED")
            .totalAmount(totalAmount)
            .discountAmount(discountAmount)
            .finalAmount(finalAmount)
            .items(orderItemInfos)
            .createdAt(now)
            .message("Order created successfully")
            .build();
    }

    @Operation(summary = "주문 목록 조회", description = "사용자의 주문 목록을 페이지 단위로 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = OrderListResponse.class)
            )
        )
    })
    @GetMapping
    public OrderListResponse getOrders(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기", example = "10")
        @RequestParam(defaultValue = "10") int size
    ) {
        List<OrderListResponse.OrderSummary> userOrders = ORDERS.values().stream()
            .filter(order -> userId.equals(order.get("userId")))
            .sorted((a, b) -> ((LocalDateTime) b.get("createdAt")).compareTo((LocalDateTime) a.get("createdAt")))
            .map(order -> {
                Long orderId = (Long) order.get("id");
                List<Map<String, Object>> items = ORDER_ITEMS.getOrDefault(orderId, Collections.emptyList());
                return OrderListResponse.OrderSummary.builder()
                    .id(orderId)
                    .status((String) order.get("status"))
                    .finalAmount((Integer) order.get("finalAmount"))
                    .itemCount(items.size())
                    .createdAt((LocalDateTime) order.get("createdAt"))
                    .build();
            })
            .toList();

        // 페이징 처리
        int start = page * size;
        int end = Math.min(start + size, userOrders.size());
        List<OrderListResponse.OrderSummary> pagedOrders = start < userOrders.size() ?
            userOrders.subList(start, end) : Collections.emptyList();

        return OrderListResponse.builder()
            .orders(pagedOrders)
            .page(page)
            .size(size)
            .total(userOrders.size())
            .build();
    }

    @Operation(summary = "주문 상세 조회", description = "특정 주문의 상세 정보를 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = OrderDetailResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "주문을 찾을 수 없음")
    })
    @GetMapping("/{orderId}")
    public OrderDetailResponse getOrderDetail(
        @Parameter(description = "주문 ID", example = "1")
        @PathVariable Long orderId,
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId
    ) {
        Map<String, Object> order = ORDERS.get(orderId);
        if (order == null || !userId.equals(order.get("userId"))) {
            throw new RuntimeException("Order not found: " + orderId);
        }

        List<Map<String, Object>> orderItems = ORDER_ITEMS.getOrDefault(orderId, Collections.emptyList());
        List<OrderDetailResponse.OrderItemDetail> itemDetails = orderItems.stream()
            .map(item -> OrderDetailResponse.OrderItemDetail.builder()
                .productName((String) item.get("productName"))
                .optionName((String) item.get("optionName"))
                .price((Integer) item.get("unitPrice"))
                .quantity((Integer) item.get("quantity"))
                .totalPrice((Integer) item.get("subtotal"))
                .build())
            .toList();

        Map<String, Object> payment = PAYMENTS.getOrDefault(orderId, Collections.emptyMap());
        String couponName = payment.containsKey("couponName") ? (String) payment.get("couponName") : null;

        return OrderDetailResponse.builder()
            .id(orderId)
            .userId((Long) order.get("userId"))
            .status((String) order.get("status"))
            .totalAmount((Integer) order.get("totalAmount"))
            .discountAmount((Integer) order.get("discountAmount"))
            .finalAmount((Integer) order.get("finalAmount"))
            .couponName(couponName)
            .items(itemDetails)
            .createdAt((LocalDateTime) order.get("createdAt"))
            .build();
    }
}
