package org.hhplus.hhecommerce.application.order;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.order.OrderDetailResponse;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.exception.OrderErrorCode;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetOrderDetailUseCase {

    private final OrderRepository orderRepository;

    public OrderDetailResponse execute(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        List<OrderDetailResponse.OrderItemDetail> items = order.getOrderItems().stream()
                .map(item -> new OrderDetailResponse.OrderItemDetail(
                        item.getProductOption().getProduct().getName(),
                        item.getProductOption().getOptionName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubTotal()
                ))
                .collect(Collectors.toList());

        return new OrderDetailResponse(
                order.getId(),
                order.getUser().getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                null,
                items,
                order.getOrderedAt()
        );
    }
}
