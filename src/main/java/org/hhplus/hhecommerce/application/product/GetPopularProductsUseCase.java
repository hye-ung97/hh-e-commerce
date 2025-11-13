package org.hhplus.hhecommerce.application.product;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.product.PopularProductsResponse;
import org.hhplus.hhecommerce.infrastructure.repository.order.OrderRepository;
import org.hhplus.hhecommerce.infrastructure.repository.order.PopularProductProjection;
import org.hhplus.hhecommerce.infrastructure.repository.product.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetPopularProductsUseCase {

    private static final int POPULAR_PRODUCT_DAYS = 3; // 최근 N일 기준
    private static final int POPULAR_PRODUCT_LIMIT = 5; // 상위 N개

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public PopularProductsResponse execute() {
        LocalDateTime startDate = LocalDateTime.now().minusDays(POPULAR_PRODUCT_DAYS);

        List<PopularProductProjection> popularProducts = orderRepository.findTopSellingProducts(startDate);

        List<PopularProductsResponse.PopularProduct> result;

        if (!popularProducts.isEmpty()) {
            result = popularProducts.stream()
                    .map(projection -> new PopularProductsResponse.PopularProduct(
                            projection.getProductId(),
                            projection.getProductName(),
                            0,
                            projection.getTotalSales().intValue(),
                            projection.getCategory(),
                            projection.getStatus()
                    ))
                    .collect(Collectors.toList());
        } else {
            result = productRepository.findAll().stream()
                    .limit(POPULAR_PRODUCT_LIMIT)
                    .map(product -> new PopularProductsResponse.PopularProduct(
                            product.getId(),
                            product.getName(),
                            0,
                            0,
                            product.getCategory(),
                            product.getStatus().name()
                    ))
                    .collect(Collectors.toList());
        }

        return new PopularProductsResponse(
                result,
                result.size()
        );
    }
}
