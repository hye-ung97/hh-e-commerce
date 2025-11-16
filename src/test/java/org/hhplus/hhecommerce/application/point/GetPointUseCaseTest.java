package org.hhplus.hhecommerce.application.point;

import org.hhplus.hhecommerce.api.dto.point.PointResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPointUseCaseTest {

    @Mock
    private PointRepository pointRepository;

    @InjectMocks
    private GetPointUseCase getPointUseCase;

    @Test
    @DisplayName("정상적으로 포인트를 조회할 수 있다")
    void 정상적으로_포인트를_조회할_수_있다() {
        // Given
        Long userId = 1L;
        Point point = new Point(userId);
        point.charge(1000);

        when(pointRepository.findByUserId(userId)).thenReturn(Optional.of(point));

        // When
        PointResponse response = getPointUseCase.execute(userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.amount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("존재하지 않는 포인트를 조회하면 예외가 발생한다")
    void 존재하지_않는_포인트를_조회하면_예외가_발생한다() {
        // Given
        Long nonExistentUserId = 999L;

        when(pointRepository.findByUserId(nonExistentUserId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> getPointUseCase.execute(nonExistentUserId))
            .isInstanceOf(PointException.class);
    }
}
