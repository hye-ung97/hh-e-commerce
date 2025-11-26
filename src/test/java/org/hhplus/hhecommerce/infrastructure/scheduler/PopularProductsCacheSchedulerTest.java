package org.hhplus.hhecommerce.infrastructure.scheduler;

import org.hhplus.hhecommerce.api.dto.product.PopularProductsResponse;
import org.hhplus.hhecommerce.config.TestContainersConfig;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PopularProductsCacheSchedulerTest extends TestContainersConfig {

    @Autowired
    private PopularProductsCacheScheduler scheduler;

    @Autowired
    private RedisTemplate<String, Object> redisObjectTemplate;

    @Autowired
    private ProductRepository productRepository;

    private static final String CACHE_KEY = "products:popular::top5";

    @BeforeEach
    void setUp() {
        redisObjectTemplate.delete(CACHE_KEY);
    }

    @Test
    @DisplayName("스케줄러 실행 시 캐시가 정상적으로 갱신된다")
    void refreshPopularProductsCache_success() {
        // given
        createTestProducts();

        // when
        scheduler.refreshPopularProductsCache();

        // then
        Object cached = redisObjectTemplate.opsForValue().get(CACHE_KEY);
        assertThat(cached).isNotNull();
    }

    @Test
    @DisplayName("캐시가 덮어쓰기 방식으로 갱신된다")
    void refreshPopularProductsCache_overwrite() {
        // given
        createTestProducts();
        PopularProductsResponse oldData = new PopularProductsResponse(List.of(), 0);
        redisObjectTemplate.opsForValue().set(CACHE_KEY, oldData);

        // when
        scheduler.refreshPopularProductsCache();

        // then
        Object cached = redisObjectTemplate.opsForValue().get(CACHE_KEY);
        assertThat(cached).isNotNull();
    }

    @Test
    @DisplayName("분산 환경에서 동시 실행 시 락에 의해 하나만 실행된다")
    void refreshPopularProductsCache_distributedLock() throws InterruptedException {
        // given
        createTestProducts();
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger executionCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    scheduler.refreshPopularProductsCache();
                    executionCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

        // then
        Object cached = redisObjectTemplate.opsForValue().get(CACHE_KEY);
        assertThat(cached).isNotNull();
    }

    private void createTestProducts() {
        if (productRepository.count() == 0) {
            for (int i = 1; i <= 5; i++) {
                Product product = new Product(
                        "테스트 상품 " + i,
                        "테스트 설명 " + i,
                        "테스트 카테고리"
                );
                productRepository.save(product);
            }
        }
    }
}
