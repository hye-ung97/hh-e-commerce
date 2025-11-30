package org.hhplus.hhecommerce.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCacheManager {

    @Caching(evict = {
            @CacheEvict(value = "products:list", allEntries = true),
            @CacheEvict(value = "products:detail", allEntries = true)
    })
    public void evictProductCaches() {
        log.info("상품 캐시 전체 무효화 완료 (products:list, products:detail)");
    }

    @CacheEvict(value = "products:detail", key = "#productId")
    public void evictProductDetail(Long productId) {
        log.info("상품 상세 캐시 무효화 완료 - productId: {}", productId);
    }

    @CacheEvict(value = "products:list", allEntries = true)
    public void evictProductList() {
        log.info("상품 목록 캐시 무효화 완료");
    }
}
