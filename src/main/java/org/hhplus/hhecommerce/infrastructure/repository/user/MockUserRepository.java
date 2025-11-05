package org.hhplus.hhecommerce.infrastructure.repository.user;

import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MockUserRepository implements UserRepository {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockUserRepository() {
        // 초기 사용자 데이터
        User user1 = new User("김철수", "kim@example.com");
        User user2 = new User("이영희", "lee@example.com");
        User user3 = new User("박민수", "park@example.com");

        save(user1);
        save(user2);
        save(user3);
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            user.setId(idGenerator.getAndIncrement());
        }
        store.put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return store.values().stream()
                .filter(user -> user.getEmail().equals(email))
                .findFirst();
    }

    @Override
    public java.util.List<User> findAll() {
        return new java.util.ArrayList<>(store.values());
    }

    @Override
    public void delete(User user) {
        store.remove(user.getId());
    }
}
