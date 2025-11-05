package org.hhplus.hhecommerce.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.order.*;
import org.hhplus.hhecommerce.application.order.CreateOrderUseCase;
import org.hhplus.hhecommerce.application.order.GetOrderDetailUseCase;
import org.hhplus.hhecommerce.application.order.GetOrdersUseCase;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Order", description = "주문 관리 API")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrdersUseCase getOrdersUseCase;
    private final GetOrderDetailUseCase getOrderDetailUseCase;

    @Operation(summary = "주문 생성")
    @PostMapping
    public CreateOrderResponse createOrder(
        @Parameter(description = "사용자 ID") @RequestParam(defaultValue = "1") Long userId,
        @RequestBody CreateOrderRequest request
    ) {
        return createOrderUseCase.execute(userId, request);
    }

    @Operation(summary = "주문 목록 조회")
    @GetMapping
    public OrderListResponse getOrders(
        @Parameter(description = "사용자 ID") @RequestParam(defaultValue = "1") Long userId
    ) {
        return getOrdersUseCase.execute(userId);
    }

    @Operation(summary = "주문 상세 조회")
    @GetMapping("/{orderId}")
    public OrderDetailResponse getOrderDetail(
        @Parameter(description = "주문 ID") @PathVariable Long orderId
    ) {
        return getOrderDetailUseCase.execute(orderId);
    }
}
