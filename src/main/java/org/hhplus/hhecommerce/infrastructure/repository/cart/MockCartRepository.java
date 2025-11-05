package org.hhplus.hhecommerce.infrastructure.repository.cart;

import org.hhplus.hhecommerce.domain.cart.*;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class MockCartRepository implements CartRepository {
    private final Map<Long, Cart> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Cart save(Cart cart) {
        if (cart.getId() == null) cart.setId(idGenerator.getAndIncrement());
        store.put(cart.getId(), cart);
        return cart;
    }

    @Override
    public Optional<Cart> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Cart> findByUserId(Long userId) {
        return store.values().stream()
                .filter(cart -> cart.getUserId().equals(userId))
                .sorted(Comparator.comparing(Cart::getId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Cart> findByUserId(Long userId, int page, int size) {
        return store.values().stream()
                .filter(cart -> cart.getUserId().equals(userId))
                .sorted(Comparator.comparing(Cart::getId))
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    @Override
    public int countByUserId(Long userId) {
        return (int) store.values().stream()
                .filter(cart -> cart.getUserId().equals(userId))
                .count();
    }

    @Override
    public Optional<Cart> findByUserIdAndProductOptionId(Long userId, Long productOptionId) {
        return store.values().stream()
                .filter(cart -> cart.getUserId().equals(userId)
                        && cart.getProductOptionId().equals(productOptionId))
                .findFirst();
    }

    @Override
    public void delete(Cart cart) {
        store.remove(cart.getId());
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        store.values().removeIf(cart -> cart.getUserId().equals(userId));
    }
}
