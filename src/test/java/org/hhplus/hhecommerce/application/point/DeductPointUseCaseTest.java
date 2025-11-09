package org.hhplus.hhecommerce.application.point;

import org.hhplus.hhecommerce.api.dto.point.ChargeRequest;
import org.hhplus.hhecommerce.api.dto.point.DeductRequest;
import org.hhplus.hhecommerce.api.dto.point.DeductResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DeductPointUseCaseTest {

    private DeductPointUseCase deductPointUseCase;
    private ChargePointUseCase chargePointUseCase;
    private TestPointRepository pointRepository;
    private TestUserRepository userRepository;

    @BeforeEach
    void setUp() {
        pointRepository = new TestPointRepository();
        userRepository = new TestUserRepository();
        deductPointUseCase = new DeductPointUseCase(pointRepository);
        chargePointUseCase = new ChargePointUseCase(pointRepository, userRepository);
    }

    @Test
    @DisplayName("정상적으로 포인트를 차감할 수 있다")
    void 정상적으로_포인트를_차감할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(10000);
        pointRepository.save(point);

        DeductRequest request = new DeductRequest(3000);

        // When
        DeductResponse response = deductPointUseCase.execute(user.getId(), request);

        // Then
        assertNotNull(response);
        assertEquals(user.getId(), response.userId());
        assertEquals(7000, response.amount()); // 10000 - 3000
        assertEquals(3000, response.deductedAmount());
        assertEquals("Point deducted successfully", response.message());
    }

    @Test
    @DisplayName("포인트가 부족하면 차감할 수 없다")
    void 포인트가_부족하면_차감할_수_없다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(1000);
        pointRepository.save(point);

        DeductRequest request = new DeductRequest(5000);

        // When & Then
        PointException exception = assertThrows(PointException.class, () -> {
            deductPointUseCase.execute(user.getId(), request);
        });
        assertEquals(PointErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());

        // 포인트가 차감되지 않았는지 확인
        Point unchangedPoint = pointRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(1000, unchangedPoint.getAmount());
    }

    @Test
    @DisplayName("포인트가 없는 사용자는 차감할 수 없다")
    void 포인트가_없는_사용자는_차감할_수_없다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");
        userRepository.save(user);

        DeductRequest request = new DeductRequest(1000);

        // When & Then
        assertThrows(PointException.class, () -> {
            deductPointUseCase.execute(user.getId(), request);
        });
    }

    @Test
    @DisplayName("충전과 차감을 연속으로 수행할 수 있다")
    void 충전과_차감을_연속으로_수행할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");
        userRepository.save(user);

        Point point = new Point(user);
        pointRepository.save(point);

        // When
        chargePointUseCase.execute(user.getId(), new ChargeRequest(10000));
        deductPointUseCase.execute(user.getId(), new DeductRequest(3000));
        chargePointUseCase.execute(user.getId(), new ChargeRequest(5000));
        DeductResponse finalResponse = deductPointUseCase.execute(user.getId(), new DeductRequest(2000));

        // Then
        assertEquals(10000, finalResponse.amount()); // 10000 - 3000 + 5000 - 2000
    }

    // 테스트 전용 Mock Repository
    static class TestPointRepository implements PointRepository {
        private final Map<Long, Point> store = new HashMap<>();
        private final Map<Long, Point> userPointStore = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public Point save(Point point) {
            if (point.getId() == null) {
                point.setId(idCounter++);
            }
            store.put(point.getId(), point);
            userPointStore.put(point.getUser().getId(), point);
            return point;
        }

        @Override
        public Optional<Point> findByUserId(Long userId) {
            return Optional.ofNullable(userPointStore.get(userId));
        }

        @Override
        public Optional<Point> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public void delete(Point point) {
        }
    }

    static class TestUserRepository implements UserRepository {
        private final Map<Long, User> store = new HashMap<>();

        @Override
        public User save(User user) {
            store.put(user.getId(), user);
            return user;
        }

        @Override
        public Optional<User> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>();
        }

        @Override
        public void delete(User user) {
        }
    }
}
