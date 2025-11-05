package org.hhplus.hhecommerce.application.product;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.product.PopularProductsResponse;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetPopularProductsUseCase {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public PopularProductsResponse execute() {
        // 1. 모든 주문에서 상품별 판매 수량 집계
        Map<Long, Integer> productSalesMap = orderRepository.findAll().stream()
                .flatMap(order -> order.getOrderItems().stream())
                .collect(Collectors.groupingBy(
                        orderItem -> orderItem.getProductOption().getProduct().getId(),
                        Collectors.summingInt(orderItem -> orderItem.getQuantity())
                ));

        List<PopularProductsResponse.PopularProduct> popularProducts;

        // 2. 판매 데이터가 있는 경우: 판매량 기준 정렬
        if (!productSalesMap.isEmpty()) {
            List<Long> topProductIds = productSalesMap.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            popularProducts = topProductIds.stream()
                    .map(productId -> productRepository.findById(productId).orElse(null))
                    .filter(Objects::nonNull)
                    .map(product -> PopularProductsResponse.PopularProduct.builder()
                            .productId(product.getId())
                            .name(product.getName())
                            .totalSales(productSalesMap.getOrDefault(product.getId(), 0))
                            .category(product.getCategory())
                            .status(product.getStatus().name())
                            .build())
                    .collect(Collectors.toList());
        } else {
            // 3. 판매 데이터가 없는 경우: 최근 등록된 상품 5개 반환
            popularProducts = productRepository.findAll().stream()
                    .limit(5)
                    .map(product -> PopularProductsResponse.PopularProduct.builder()
                            .productId(product.getId())
                            .name(product.getName())
                            .totalSales(0)
                            .category(product.getCategory())
                            .status(product.getStatus().name())
                            .build())
                    .collect(Collectors.toList());
        }

        return PopularProductsResponse.builder()
                .products(popularProducts)
                .totalCount(popularProducts.size())
                .build();
    }
}
