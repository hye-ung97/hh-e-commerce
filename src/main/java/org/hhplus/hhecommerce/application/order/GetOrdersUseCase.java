package org.hhplus.hhecommerce.application.order;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.order.OrderListResponse;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.infrastructure.repository.order.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetOrdersUseCase {

    private final OrderRepository orderRepository;

    public OrderListResponse execute(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId);

        List<OrderListResponse.OrderSummary> orderInfos = orders.stream()
                .map(order -> new OrderListResponse.OrderSummary(
                        order.getId(),
                        order.getStatus().name(),
                        order.getFinalAmount(),
                        order.getOrderItems().size(),
                        order.getOrderedAt()
                ))
                .collect(Collectors.toList());

        return new OrderListResponse(orderInfos, 0, orderInfos.size(), orderInfos.size());
    }
}
