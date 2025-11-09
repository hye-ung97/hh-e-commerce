package org.hhplus.hhecommerce.domain.point;

import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.hhplus.hhecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PointTest {

    private User user;
    private Point point;

    @BeforeEach
    void setUp() {
        user = new User(1L, "테스트유저", "test@test.com");
        point = new Point(user);
    }

    @Test
    @DisplayName("포인트를 생성하면 초기 잔액은 0이다")
    void 포인트를_생성하면_초기_잔액은_0이다() {
        // When
        Point newPoint = new Point(user);

        // Then
        assertAll("포인트 초기 상태 검증",
            () -> assertEquals(0, newPoint.getAmount()),
            () -> assertEquals(user, newPoint.getUser())
        );
    }

    @Test
    @DisplayName("정상적으로 포인트를 충전할 수 있다")
    void 정상적으로_포인트를_충전할_수_있다() {
        // When
        point.charge(5000);

        // Then
        assertEquals(5000, point.getAmount());
    }

    @Test
    @DisplayName("포인트를 여러 번 충전하면 누적된다")
    void 포인트를_여러_번_충전하면_누적된다() {
        // When
        point.charge(1000);
        point.charge(2000);
        point.charge(3000);

        // Then
        assertEquals(6000, point.getAmount());
    }

    @Test
    @DisplayName("0 이하의 금액으로 충전할 수 없다")
    void 영_이하의_금액으로_충전할_수_없다() {
        // When & Then
        PointException exception1 = assertThrows(PointException.class, () -> {
            point.charge(0);
        });
        PointException exception2 = assertThrows(PointException.class, () -> {
            point.charge(-1000);
        });

        assertAll("잘못된 충전 금액 검증",
            () -> assertEquals(PointErrorCode.INVALID_AMOUNT, exception1.getErrorCode()),
            () -> assertEquals(PointErrorCode.INVALID_AMOUNT, exception2.getErrorCode()),
            () -> assertEquals(0, point.getAmount()) // 잔액은 변하지 않음
        );
    }

    @Test
    @DisplayName("정상적으로 포인트를 차감할 수 있다")
    void 정상적으로_포인트를_차감할_수_있다() {
        // Given
        point.charge(10000);

        // When
        point.deduct(3000);

        // Then
        assertEquals(7000, point.getAmount());
    }

    @Test
    @DisplayName("포인트를 여러 번 차감할 수 있다")
    void 포인트를_여러_번_차감할_수_있다() {
        // Given
        point.charge(10000);

        // When
        point.deduct(1000);
        point.deduct(2000);
        point.deduct(3000);

        // Then
        assertEquals(4000, point.getAmount()); // 10000 - 1000 - 2000 - 3000
    }

    @Test
    @DisplayName("포인트가 부족하면 차감할 수 없다")
    void 포인트가_부족하면_차감할_수_없다() {
        // Given
        point.charge(1000);

        // When & Then
        PointException exception = assertThrows(PointException.class, () -> {
            point.deduct(5000);
        });

        assertAll("잔액 부족 검증",
            () -> assertEquals(PointErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode()),
            () -> assertTrue(exception.getMessage().contains("부족")),
            () -> assertEquals(1000, point.getAmount()) // 잔액은 변하지 않음
        );
    }

    @Test
    @DisplayName("0 이하의 금액으로 차감할 수 없다")
    void 영_이하의_금액으로_차감할_수_없다() {
        // Given
        point.charge(10000);

        // When & Then
        PointException exception1 = assertThrows(PointException.class, () -> {
            point.deduct(0);
        });
        PointException exception2 = assertThrows(PointException.class, () -> {
            point.deduct(-1000);
        });

        assertAll("잘못된 차감 금액 검증",
            () -> assertEquals(PointErrorCode.INVALID_AMOUNT, exception1.getErrorCode()),
            () -> assertEquals(PointErrorCode.INVALID_AMOUNT, exception2.getErrorCode()),
            () -> assertEquals(10000, point.getAmount()) // 잔액은 변하지 않음
        );
    }

    @Test
    @DisplayName("정확히 남은 포인트만큼 차감할 수 있다")
    void 정확히_남은_포인트만큼_차감할_수_있다() {
        // Given
        point.charge(5000);

        // When
        point.deduct(5000);

        // Then
        assertEquals(0, point.getAmount());
    }

    @Test
    @DisplayName("포인트가 0일 때는 차감할 수 없다")
    void 포인트가_0일_때는_차감할_수_없다() {
        // When & Then
        PointException exception = assertThrows(PointException.class, () -> {
            point.deduct(100);
        });
        assertEquals(PointErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
    }

    @Test
    @DisplayName("충분한 포인트가 있는지 확인할 수 있다")
    void 충분한_포인트가_있는지_확인할_수_있다() {
        // Given
        point.charge(10000);

        // When & Then
        assertAll("포인트 충분성 검증",
            () -> assertTrue(point.hasEnoughPoint(5000)),
            () -> assertTrue(point.hasEnoughPoint(10000)),
            () -> assertFalse(point.hasEnoughPoint(10001)),
            () -> assertFalse(point.hasEnoughPoint(20000))
        );
    }

    @Test
    @DisplayName("포인트가 0일 때 충분한 포인트가 있는지 확인할 수 있다")
    void 포인트가_0일_때_충분한_포인트가_있는지_확인할_수_있다() {
        // When & Then
        assertAll("잔액 0일 때 충분성 검증",
            () -> assertTrue(point.hasEnoughPoint(0)),
            () -> assertFalse(point.hasEnoughPoint(1))
        );
    }

    @Test
    @DisplayName("충전과 차감을 연속으로 수행할 수 있다")
    void 충전과_차감을_연속으로_수행할_수_있다() {
        // When
        point.charge(10000);
        point.deduct(3000);
        point.charge(5000);
        point.deduct(2000);

        // Then
        assertEquals(10000, point.getAmount()); // 10000 - 3000 + 5000 - 2000
    }

    @Test
    @DisplayName("ID를 포함한 생성자로 포인트를 생성할 수 있다")
    void ID를_포함한_생성자로_포인트를_생성할_수_있다() {
        // When
        Point newPoint = new Point(999L, user, 50000);

        // Then
        assertAll("ID 포함 생성자 검증",
            () -> assertEquals(999L, newPoint.getId()),
            () -> assertEquals(user, newPoint.getUser()),
            () -> assertEquals(50000, newPoint.getAmount())
        );
    }
}
