package org.hhplus.hhecommerce.application.point;

import org.hhplus.hhecommerce.api.dto.point.ChargeRequest;
import org.hhplus.hhecommerce.api.dto.point.ChargeResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargePointUseCaseTest {

    @Mock
    private PointRepository pointRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChargePointUseCase chargePointUseCase;

    @Test
    @DisplayName("정상적으로 포인트를 충전할 수 있다")
    void 정상적으로_포인트를_충전할_수_있다() {
        // Given
        Long userId = 1L;
        Point point = new Point(userId);
        point.charge(5000);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(pointRepository.findByUserId(userId))
                .thenReturn(Optional.of(new Point(userId)))
                .thenReturn(Optional.of(point));
        when(pointRepository.chargePoint(eq(userId), eq(5000))).thenReturn(1);

        ChargeRequest request = new ChargeRequest(5000);

        // When
        ChargeResponse response = chargePointUseCase.execute(userId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isEqualTo(5000);
        assertThat(response.chargedAmount()).isEqualTo(5000);
        assertThat(response.message()).isEqualTo("Point charged successfully");
    }

    @Test
    @DisplayName("포인트가 없는 사용자도 충전하면 포인트가 생성된다")
    void 포인트가_없는_사용자도_충전하면_포인트가_생성된다() {
        // Given
        Long userId = 1L;
        Point chargedPoint = new Point(userId);
        chargedPoint.setId(1L);
        chargedPoint.charge(10000);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(pointRepository.findByUserId(userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(chargedPoint));
        when(pointRepository.save(any(Point.class))).thenAnswer(invocation -> {
            Point savedPoint = invocation.getArgument(0);
            savedPoint.setId(1L);
            return savedPoint;
        });
        when(pointRepository.chargePoint(eq(userId), eq(10000))).thenReturn(1);

        ChargeRequest request = new ChargeRequest(10000);

        // When
        ChargeResponse response = chargePointUseCase.execute(userId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isEqualTo(10000);
    }

    @Test
    @DisplayName("여러 번 충전하면 포인트가 누적된다")
    void 여러_번_충전하면_포인트가_누적된다() {
        // Given
        Long userId = 1L;
        Point point1 = new Point(userId);
        point1.charge(1000);
        Point point2 = new Point(userId);
        point2.charge(3000);
        Point point3 = new Point(userId);
        point3.charge(6000);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(pointRepository.findByUserId(userId))
                .thenReturn(Optional.of(new Point(userId)))
                .thenReturn(Optional.of(point1))
                .thenReturn(Optional.of(point1))
                .thenReturn(Optional.of(point2))
                .thenReturn(Optional.of(point2))
                .thenReturn(Optional.of(point3));
        when(pointRepository.chargePoint(eq(userId), anyInt())).thenReturn(1);

        // When
        chargePointUseCase.execute(userId, new ChargeRequest(1000));
        chargePointUseCase.execute(userId, new ChargeRequest(2000));
        ChargeResponse response = chargePointUseCase.execute(userId, new ChargeRequest(3000));

        // Then
        assertThat(response.amount()).isEqualTo(6000);
    }

    @Test
    @DisplayName("최대 잔액을 초과하면 충전할 수 없다")
    void 최대_잔액을_초과하면_충전할_수_없다() {
        // Given
        Long userId = 1L;
        Point point = new Point(userId);
        point.charge(90000);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(pointRepository.findByUserId(userId)).thenReturn(Optional.of(point));
        when(pointRepository.chargePoint(eq(userId), eq(20000))).thenReturn(0);

        ChargeRequest request = new ChargeRequest(20000);

        // When & Then
        assertThatThrownBy(() -> chargePointUseCase.execute(userId, request))
                .isInstanceOf(PointException.class)
                .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.EXCEED_MAX_BALANCE);
    }
}
