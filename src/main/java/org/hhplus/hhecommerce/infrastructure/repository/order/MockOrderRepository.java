package org.hhplus.hhecommerce.infrastructure.repository.order;

import org.hhplus.hhecommerce.domain.order.*;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class MockOrderRepository implements OrderRepository {
    private final Map<Long, Order> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Order save(Order order) {
        if (order.getId() == null) order.setId(idGenerator.getAndIncrement());
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return store.values().stream()
                .filter(order -> order.getUser().getId().equals(userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(store.values());
    }
}
