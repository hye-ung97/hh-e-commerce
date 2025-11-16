package org.hhplus.hhecommerce.application.point;

import org.hhplus.hhecommerce.api.dto.point.ChargeRequest;
import org.hhplus.hhecommerce.api.dto.point.ChargeResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.user.User;
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
        User user = new User(1L, "테스트유저", "test@test.com");

        Point point = new Point(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointRepository.findByUserId(1L)).thenReturn(Optional.of(point));
        when(pointRepository.save(any(Point.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChargeRequest request = new ChargeRequest(5000);

        // When
        ChargeResponse response = chargePointUseCase.execute(user.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.amount()).isEqualTo(5000);
        assertThat(response.chargedAmount()).isEqualTo(5000);
        assertThat(response.message()).isEqualTo("Point charged successfully");
    }

    @Test
    @DisplayName("포인트가 없는 사용자도 충전하면 포인트가 생성된다")
    void 포인트가_없는_사용자도_충전하면_포인트가_생성된다() {
        // Given
        User user = new User(1L, "신규유저", "new@test.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(pointRepository.save(any(Point.class))).thenAnswer(invocation -> {
            Point savedPoint = invocation.getArgument(0);
            savedPoint.setId(1L);
            return savedPoint;
        });

        ChargeRequest request = new ChargeRequest(10000);

        // When
        ChargeResponse response = chargePointUseCase.execute(user.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.amount()).isEqualTo(10000);
    }

    @Test
    @DisplayName("여러 번 충전하면 포인트가 누적된다")
    void 여러_번_충전하면_포인트가_누적된다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Point point = new Point(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointRepository.findByUserId(1L)).thenReturn(Optional.of(point));
        when(pointRepository.save(any(Point.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        chargePointUseCase.execute(user.getId(), new ChargeRequest(1000));
        chargePointUseCase.execute(user.getId(), new ChargeRequest(2000));
        ChargeResponse response = chargePointUseCase.execute(user.getId(), new ChargeRequest(3000));

        // Then
        assertThat(response.amount()).isEqualTo(6000); // 1000 + 2000 + 3000
    }
}
