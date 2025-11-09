package org.hhplus.hhecommerce.application.point;

import org.hhplus.hhecommerce.api.dto.point.ChargeRequest;
import org.hhplus.hhecommerce.api.dto.point.ChargeResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ChargePointUseCaseTest {

    private ChargePointUseCase chargePointUseCase;
    private TestPointRepository pointRepository;
    private TestUserRepository userRepository;

    @BeforeEach
    void setUp() {
        pointRepository = new TestPointRepository();
        userRepository = new TestUserRepository();
        chargePointUseCase = new ChargePointUseCase(pointRepository, userRepository);
    }

    @Test
    @DisplayName("정상적으로 포인트를 충전할 수 있다")
    void 정상적으로_포인트를_충전할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");
        userRepository.save(user);

        Point point = new Point(user);
        pointRepository.save(point);

        ChargeRequest request = new ChargeRequest(5000);

        // When
        ChargeResponse response = chargePointUseCase.execute(user.getId(), request);

        // Then
        assertAll("ChargeResponse 검증",
            () -> assertNotNull(response),
            () -> assertEquals(user.getId(), response.userId()),
            () -> assertEquals(5000, response.amount()),
            () -> assertEquals(5000, response.chargedAmount()),
            () -> assertEquals("Point charged successfully", response.message())
        );
    }

    @Test
    @DisplayName("포인트가 없는 사용자도 충전하면 포인트가 생성된다")
    void 포인트가_없는_사용자도_충전하면_포인트가_생성된다() {
        // Given
        User user = new User(1L, "신규유저", "new@test.com");
        userRepository.save(user);

        ChargeRequest request = new ChargeRequest(10000);

        // When
        ChargeResponse response = chargePointUseCase.execute(user.getId(), request);

        // Then
        Optional<Point> createdPoint = pointRepository.findByUserId(user.getId());
        assertAll("포인트 생성 및 충전 검증",
            () -> assertNotNull(response),
            () -> assertEquals(user.getId(), response.userId()),
            () -> assertEquals(10000, response.amount()),
            () -> assertTrue(createdPoint.isPresent()),
            () -> assertEquals(10000, createdPoint.get().getAmount())
        );
    }

    @Test
    @DisplayName("여러 번 충전하면 포인트가 누적된다")
    void 여러_번_충전하면_포인트가_누적된다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");
        userRepository.save(user);

        Point point = new Point(user);
        pointRepository.save(point);

        // When
        chargePointUseCase.execute(user.getId(), new ChargeRequest(1000));
        chargePointUseCase.execute(user.getId(), new ChargeRequest(2000));
        ChargeResponse response = chargePointUseCase.execute(user.getId(), new ChargeRequest(3000));

        // Then
        assertEquals(6000, response.amount()); // 1000 + 2000 + 3000
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
