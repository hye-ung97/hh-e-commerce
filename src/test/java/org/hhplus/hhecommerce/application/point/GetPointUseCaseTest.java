package org.hhplus.hhecommerce.application.point;

import org.hhplus.hhecommerce.api.dto.point.PointResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class GetPointUseCaseTest {

    private GetPointUseCase getPointUseCase;
    private TestPointRepository pointRepository;
    private TestUserRepository userRepository;

    @BeforeEach
    void setUp() {
        pointRepository = new TestPointRepository();
        userRepository = new TestUserRepository();
        getPointUseCase = new GetPointUseCase(pointRepository);
    }

    @Test
    @DisplayName("정상적으로 포인트를 조회할 수 있다")
    void 정상적으로_포인트를_조회할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(1000);
        pointRepository.save(point);

        // When
        PointResponse response = getPointUseCase.execute(user.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.amount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("존재하지 않는 포인트를 조회하면 예외가 발생한다")
    void 존재하지_않는_포인트를_조회하면_예외가_발생한다() {
        // Given
        Long nonExistentUserId = 999L;

        // When & Then
        assertThatThrownBy(() -> getPointUseCase.execute(nonExistentUserId))
            .isInstanceOf(PointException.class);
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
