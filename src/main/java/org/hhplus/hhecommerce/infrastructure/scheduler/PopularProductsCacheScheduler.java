package org.hhplus.hhecommerce.infrastructure.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.product.PopularProductsResponse;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.PopularProductProjection;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PopularProductsCacheScheduler {

    private static final String CACHE_KEY = "products:popular::top5";
    private static final String LOCK_KEY = "scheduler:popular-products:lock";
    private static final int POPULAR_PRODUCT_DAYS = 3;
    private static final int POPULAR_PRODUCT_LIMIT = 5;
    private static final long LOCK_WAIT_TIME = 0L;
    private static final long LOCK_LEASE_TIME = 60L;
    private static final Duration CACHE_TTL = Duration.ofHours(48);

    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisObjectTemplate;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    private final Counter schedulerSuccessCounter;
    private final Counter schedulerFailureCounter;
    private final Timer schedulerExecutionTimer;
    private final AtomicLong lastSuccessTimestamp = new AtomicLong(0);

    public PopularProductsCacheScheduler(RedissonClient redissonClient,
                                          RedisTemplate<String, Object> redisObjectTemplate,
                                          OrderRepository orderRepository,
                                          ProductRepository productRepository,
                                          MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.redisObjectTemplate = redisObjectTemplate;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;

        this.schedulerSuccessCounter = Counter.builder("scheduler.popular_products.success")
                .description("인기 상품 캐시 갱신 성공 횟수")
                .register(meterRegistry);

        this.schedulerFailureCounter = Counter.builder("scheduler.popular_products.failure")
                .description("인기 상품 캐시 갱신 실패 횟수")
                .register(meterRegistry);

        this.schedulerExecutionTimer = Timer.builder("scheduler.popular_products.execution.time")
                .description("인기 상품 캐시 갱신 실행 시간")
                .register(meterRegistry);

        Gauge.builder("scheduler.popular_products.seconds_since_last_success", lastSuccessTimestamp,
                        timestamp -> timestamp.get() == 0 ? 0 : (System.currentTimeMillis() - timestamp.get()) / 1000.0)
                .description("마지막 성공 이후 경과 시간 (초)")
                .register(meterRegistry);
    }

    @PostConstruct
    public void initCache() {
        if (!Boolean.TRUE.equals(redisObjectTemplate.hasKey(CACHE_KEY))) {
            log.info("서버 시작 시 인기 상품 캐시 초기화");
            refreshPopularProductsCache();
        }
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void refreshPopularProductsCache() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        long startTime = System.nanoTime();

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!acquired) {
                log.info("다른 인스턴스에서 인기 상품 캐시 갱신 중. 스킵합니다.");
                return;
            }

            log.info("인기 상품 캐시 갱신 시작");

            PopularProductsResponse response = fetchPopularProducts();

            redisObjectTemplate.opsForValue().set(CACHE_KEY, response, CACHE_TTL);

            schedulerSuccessCounter.increment();
            lastSuccessTimestamp.set(System.currentTimeMillis());

            log.info("인기 상품 캐시 갱신 완료. 상품 수: {}", response.totalCount());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            schedulerFailureCounter.increment();
            log.error("인기 상품 캐시 갱신 중 인터럽트 발생", e);
        } catch (Exception e) {
            schedulerFailureCounter.increment();
            log.error("인기 상품 캐시 갱신 실패", e);
        } finally {
            long duration = System.nanoTime() - startTime;
            schedulerExecutionTimer.record(duration, TimeUnit.NANOSECONDS);

            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("스케줄러 락 해제");
            }
        }
    }

    private PopularProductsResponse fetchPopularProducts() {
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

        return new PopularProductsResponse(result, result.size());
    }
}
