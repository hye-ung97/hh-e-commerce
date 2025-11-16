package org.hhplus.hhecommerce.application.order;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.order.OrderDetailResponse;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderItem;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.exception.OrderErrorCode;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetOrderDetailUseCase {

    private final OrderRepository orderRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductRepository productRepository;

    public OrderDetailResponse execute(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        Map<Long, ProductOption> productOptionMap = order.getOrderItems().stream()
                .map(OrderItem::getProductOptionId)
                .distinct()
                .collect(Collectors.toMap(
                        optionId -> optionId,
                        optionId -> productOptionRepository.findById(optionId)
                                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND))
                ));

        Map<Long, Product> productMap = productOptionMap.values().stream()
                .map(ProductOption::getProductId)
                .distinct()
                .collect(Collectors.toMap(
                        productId -> productId,
                        productId -> productRepository.findById(productId)
                                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND))
                ));

        List<OrderDetailResponse.OrderItemDetail> items = order.getOrderItems().stream()
                .map(item -> {
                    ProductOption option = productOptionMap.get(item.getProductOptionId());
                    Product product = productMap.get(option.getProductId());
                    return new OrderDetailResponse.OrderItemDetail(
                            product.getName(),
                            option.getOptionName(),
                            item.getUnitPrice(),
                            item.getQuantity(),
                            item.getSubTotal()
                    );
                })
                .collect(Collectors.toList());

        return new OrderDetailResponse(
                order.getId(),
                order.getUserId(),
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
