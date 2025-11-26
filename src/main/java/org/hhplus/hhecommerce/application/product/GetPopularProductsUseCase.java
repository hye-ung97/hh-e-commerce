package org.hhplus.hhecommerce.application.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.product.PopularProductsResponse;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.PopularProductProjection;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetPopularProductsUseCase {

    private static final String CACHE_KEY = "products:popular::top5";
    private static final int POPULAR_PRODUCT_DAYS = 3;
    private static final int POPULAR_PRODUCT_LIMIT = 5;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final RedisTemplate<String, Object> redisObjectTemplate;
    private final ObjectMapper objectMapper;

    public PopularProductsResponse execute() {
        Object cached = redisObjectTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            log.debug("인기 상품 캐시 히트");
            return objectMapper.convertValue(cached, PopularProductsResponse.class);
        }

        log.debug("인기 상품 캐시 미스. DB에서 조회");
        return fetchFromDatabase();
    }

    private PopularProductsResponse fetchFromDatabase() {
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
