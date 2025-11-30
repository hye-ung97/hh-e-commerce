package org.hhplus.hhecommerce.application.order;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderResponse;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.OrderStatus;
import org.hhplus.hhecommerce.domain.order.exception.OrderErrorCode;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private OrderTransactionService orderTransactionService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RLock rLock;

    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    void setUp() throws InterruptedException {
        createOrderUseCase = new CreateOrderUseCase(
                redissonClient,
                orderTransactionService,
                orderRepository,
                new SimpleMeterRegistry()
        );

        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        lenient().when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);

        lenient().when(orderRepository.existsByUserIdAndStatus(anyLong(), any(OrderStatus.class))).thenReturn(false);
    }

    @Test
    @DisplayName("정상적으로 주문을 생성할 수 있다")
    void 정상적으로_주문을_생성할_수_있다() {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(null);
        CreateOrderResponse expectedResponse = new CreateOrderResponse(
                1L, userId, "PENDING", 100000, 0, 100000,
                List.of(), null, "주문이 완료되었습니다"
        );

        when(orderTransactionService.executeOrderLogic(userId, request))
                .thenReturn(expectedResponse);

        // When
        CreateOrderResponse response = createOrderUseCase.execute(userId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        verify(orderTransactionService).executeOrderLogic(userId, request);
    }

    @Test
    @DisplayName("장바구니가 비어있으면 주문할 수 없다")
    void 장바구니가_비어있으면_주문할_수_없다() {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(null);

        when(orderTransactionService.executeOrderLogic(userId, request))
                .thenThrow(new OrderException(OrderErrorCode.EMPTY_CART));

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, request))
            .isInstanceOf(OrderException.class);
    }

    @Test
    @DisplayName("재고가 부족하면 주문할 수 없다")
    void 재고가_부족하면_주문할_수_없다() {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(null);

        when(orderTransactionService.executeOrderLogic(userId, request))
                .thenThrow(ProductException.class);

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, request))
            .isInstanceOf(ProductException.class);
    }

    @Test
    @DisplayName("포인트가 부족하면 주문할 수 없다")
    void 포인트가_부족하면_주문할_수_없다() {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(null);

        when(orderTransactionService.executeOrderLogic(userId, request))
                .thenThrow(new PointException(PointErrorCode.INSUFFICIENT_BALANCE));

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, request))
            .isInstanceOf(PointException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("주문 완료 후 재고가 차감된다")
    void 주문_완료_후_재고가_차감된다() {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(null);
        CreateOrderResponse expectedResponse = new CreateOrderResponse(
                1L, userId, "PENDING", 60000, 0, 60000,
                List.of(), null, "주문이 완료되었습니다"
        );

        when(orderTransactionService.executeOrderLogic(userId, request))
                .thenReturn(expectedResponse);

        // When
        createOrderUseCase.execute(userId, request);

        // Then
        verify(orderTransactionService, times(1)).executeOrderLogic(userId, request);
    }

    @Test
    @DisplayName("주문 완료 후 장바구니가 비워진다")
    void 주문_완료_후_장바구니가_비워진다() {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(null);
        CreateOrderResponse expectedResponse = new CreateOrderResponse(
                1L, userId, "PENDING", 100000, 0, 100000,
                List.of(), null, "주문이 완료되었습니다"
        );

        when(orderTransactionService.executeOrderLogic(userId, request))
                .thenReturn(expectedResponse);

        // When
        createOrderUseCase.execute(userId, request);

        // Then
        verify(orderTransactionService, times(1)).executeOrderLogic(userId, request);
    }

    @Test
    @DisplayName("쿠폰을 적용하여 주문할 수 있다")
    void 쿠폰을_적용하여_주문할_수_있다() {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(1L);
        CreateOrderResponse expectedResponse = new CreateOrderResponse(
                1L, userId, "PENDING", 100000, 10000, 90000,
                List.of(), null, "주문이 완료되었습니다"
        );

        when(orderTransactionService.executeOrderLogic(userId, request))
                .thenReturn(expectedResponse);

        // When
        CreateOrderResponse response = createOrderUseCase.execute(userId, request);

        // Then
        assertThat(response.totalAmount()).isEqualTo(100000);
        assertThat(response.discountAmount()).isEqualTo(10000);
        assertThat(response.finalAmount()).isEqualTo(90000);
    }

    @Test
    @DisplayName("최소 주문 금액을 만족하지 못하면 쿠폰을 사용할 수 없다")
    void 최소_주문_금액을_만족하지_못하면_쿠폰을_사용할_수_없다() {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(1L);

        when(orderTransactionService.executeOrderLogic(userId, request))
                .thenThrow(CouponException.class);

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, request))
            .isInstanceOf(CouponException.class);
    }

    @Test
    @DisplayName("이미 사용한 쿠폰은 주문에 사용할 수 없다")
    void 이미_사용한_쿠폰은_주문에_사용할_수_없다() {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(1L);

        when(orderTransactionService.executeOrderLogic(userId, request))
                .thenThrow(CouponException.class);

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, request))
            .isInstanceOf(CouponException.class);
    }

    @Test
    @DisplayName("이미 진행 중인 주문이 있으면 새로운 주문을 생성할 수 없다")
    void 이미_진행_중인_주문이_있으면_새로운_주문을_생성할_수_없다() throws InterruptedException {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(null);

        when(orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PENDING)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, request))
            .isInstanceOf(OrderException.class)
            .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_IN_PROGRESS);

        verify(rLock, never()).unlock();
        verify(orderTransactionService, never()).executeOrderLogic(any(), any());
    }

    @Test
    @DisplayName("락 획득에 실패하면 예외가 발생한다")
    void 락_획득_실패_시_예외_발생() throws InterruptedException {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(null);

        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(userId, request))
            .isInstanceOf(OrderException.class)
            .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.LOCK_ACQUISITION_FAILED);

        verify(rLock, never()).unlock();
    }
}
