package org.hhplus.hhecommerce.application.point;

import org.hhplus.hhecommerce.api.dto.point.DeductRequest;
import org.hhplus.hhecommerce.api.dto.point.DeductResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeductPointUseCaseTest {

    @Mock
    private PointRepository pointRepository;

    @InjectMocks
    private DeductPointUseCase deductPointUseCase;

    @Test
    @DisplayName("정상적으로 포인트를 차감할 수 있다")
    void 정상적으로_포인트를_차감할_수_있다() {
        // Given
        Long userId = 1L;
        Point point = new Point(userId);
        point.charge(10000);

        when(pointRepository.findByUserId(userId)).thenReturn(Optional.of(point));
        when(pointRepository.save(any(Point.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeductRequest request = new DeductRequest(3000);

        // When
        DeductResponse response = deductPointUseCase.execute(userId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isEqualTo(7000); // 10000 - 3000
        assertThat(response.deductedAmount()).isEqualTo(3000);
        assertThat(response.message()).isEqualTo("Point deducted successfully");
    }

    @Test
    @DisplayName("포인트가 부족하면 차감할 수 없다")
    void 포인트가_부족하면_차감할_수_없다() {
        // Given
        Long userId = 1L;
        Point point = new Point(userId);
        point.charge(1000);

        when(pointRepository.findByUserId(userId)).thenReturn(Optional.of(point));

        DeductRequest request = new DeductRequest(5000);

        // When & Then
        assertThatThrownBy(() -> deductPointUseCase.execute(userId, request))
            .isInstanceOf(PointException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("포인트가 없는 사용자는 차감할 수 없다")
    void 포인트가_없는_사용자는_차감할_수_없다() {
        // Given
        when(pointRepository.findByUserId(1L)).thenReturn(Optional.empty());

        DeductRequest request = new DeductRequest(1000);

        // When & Then
        assertThatThrownBy(() -> deductPointUseCase.execute(1L, request))
            .isInstanceOf(PointException.class);
    }

    @Test
    @DisplayName("충전과 차감을 연속으로 수행할 수 있다")
    void 충전과_차감을_연속으로_수행할_수_있다() {
        // Given
        Long userId = 1L;
        Point point = new Point(userId);

        when(pointRepository.findByUserId(userId)).thenReturn(Optional.of(point));
        when(pointRepository.save(any(Point.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        point.charge(10000);
        deductPointUseCase.execute(userId, new DeductRequest(3000));
        point.charge(5000);
        DeductResponse finalResponse = deductPointUseCase.execute(userId, new DeductRequest(2000));

        // Then
        assertThat(finalResponse.amount()).isEqualTo(10000); // 10000 - 3000 + 5000 - 2000
    }
}
