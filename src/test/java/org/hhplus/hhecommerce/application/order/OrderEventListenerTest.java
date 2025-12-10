package org.hhplus.hhecommerce.application.order;

import org.hhplus.hhecommerce.application.ranking.UpdateProductRankingUseCase;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.cache.ProductCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventListener 테스트")
class OrderEventListenerTest {

    @Mock
    private UpdateProductRankingUseCase updateProductRankingUseCase;

    @Mock
    private ProductCacheManager productCacheManager;

    @InjectMocks
    private OrderEventListener orderEventListener;

    @Nested
    @DisplayName("handleOrderCompleted 테스트")
    class HandleOrderCompletedTest {

        @Test
        @DisplayName("주문 완료 시 캐시를 무효화한다")
        void shouldEvictCachesOnOrderCompleted() {
            // given
            OrderCompletedEvent event = new OrderCompletedEvent(1L, Map.of(1L, 2));

            // when
            orderEventListener.handleOrderCompleted(event);

            // then
            verify(productCacheManager).evictProductCaches();
        }

        @Test
        @DisplayName("주문 완료 시 상품 랭킹을 업데이트한다")
        void shouldUpdateRankingOnOrderCompleted() {
            // given
            OrderCompletedEvent event = new OrderCompletedEvent(1L, Map.of(1L, 2, 2L, 3));

            // when
            orderEventListener.handleOrderCompleted(event);

            // then
            verify(updateProductRankingUseCase).execute(1L, 2);
            verify(updateProductRankingUseCase).execute(2L, 3);
        }

        @Test
        @DisplayName("productId가 0 이하면 랭킹 업데이트를 건너뛴다")
        void shouldSkipRankingUpdateForInvalidProductId() {
            // given
            OrderCompletedEvent event = new OrderCompletedEvent(1L, Map.of(-1L, 2, 0L, 3, 1L, 5));

            // when
            orderEventListener.handleOrderCompleted(event);

            // then
            verify(updateProductRankingUseCase).execute(1L, 5);
            verify(updateProductRankingUseCase, never()).execute(eq(-1L), anyInt());
            verify(updateProductRankingUseCase, never()).execute(eq(0L), anyInt());
        }

        @Test
        @DisplayName("빈 productQuantityMap이면 랭킹 업데이트를 하지 않는다")
        void shouldNotUpdateRankingWhenProductMapIsEmpty() {
            // given
            OrderCompletedEvent event = new OrderCompletedEvent(1L, Collections.emptyMap());

            // when
            orderEventListener.handleOrderCompleted(event);

            // then
            verify(updateProductRankingUseCase, never()).execute(anyLong(), anyInt());
            verify(productCacheManager).evictProductCaches();
        }
    }

    @Nested
    @DisplayName("예외 처리 테스트")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("캐시 무효화 실패 시에도 랭킹 업데이트가 수행된다")
        void shouldUpdateRankingEvenWhenCacheEvictionFails() {
            // given
            OrderCompletedEvent event = new OrderCompletedEvent(1L, Map.of(1L, 2));
            doThrow(new RuntimeException("캐시 무효화 실패")).when(productCacheManager).evictProductCaches();

            // when
            orderEventListener.handleOrderCompleted(event);

            // then
            verify(productCacheManager).evictProductCaches();
            verify(updateProductRankingUseCase).execute(1L, 2);
        }

        @Test
        @DisplayName("랭킹 업데이트 실패 시에도 예외가 전파되지 않는다")
        void shouldNotPropagateExceptionWhenRankingUpdateFails() {
            // given
            OrderCompletedEvent event = new OrderCompletedEvent(1L, Map.of(1L, 2));
            doThrow(new RuntimeException("랭킹 업데이트 실패")).when(updateProductRankingUseCase).execute(anyLong(), anyInt());

            // when & then - 예외가 발생하지 않음
            orderEventListener.handleOrderCompleted(event);

            verify(productCacheManager).evictProductCaches();
            verify(updateProductRankingUseCase).execute(1L, 2);
        }

        @Test
        @DisplayName("캐시와 랭킹 모두 실패해도 예외가 전파되지 않는다")
        void shouldNotPropagateExceptionWhenBothFail() {
            // given
            OrderCompletedEvent event = new OrderCompletedEvent(1L, Map.of(1L, 2));
            doThrow(new RuntimeException("캐시 무효화 실패")).when(productCacheManager).evictProductCaches();
            doThrow(new RuntimeException("랭킹 업데이트 실패")).when(updateProductRankingUseCase).execute(anyLong(), anyInt());

            // when & then - 예외가 발생하지 않음
            orderEventListener.handleOrderCompleted(event);

            verify(productCacheManager).evictProductCaches();
            verify(updateProductRankingUseCase).execute(1L, 2);
        }
    }

    @Nested
    @DisplayName("다중 상품 처리 테스트")
    class MultipleProductsTest {

        @Test
        @DisplayName("여러 상품에 대해 각각 랭킹 업데이트가 수행된다")
        void shouldUpdateRankingForEachProduct() {
            // given
            Map<Long, Integer> productQuantityMap = Map.of(
                    1L, 2,
                    2L, 5,
                    3L, 1
            );
            OrderCompletedEvent event = new OrderCompletedEvent(1L, productQuantityMap);

            // when
            orderEventListener.handleOrderCompleted(event);

            // then
            verify(updateProductRankingUseCase).execute(1L, 2);
            verify(updateProductRankingUseCase).execute(2L, 5);
            verify(updateProductRankingUseCase).execute(3L, 1);
            verify(updateProductRankingUseCase, times(3)).execute(anyLong(), anyInt());
        }
    }
}
