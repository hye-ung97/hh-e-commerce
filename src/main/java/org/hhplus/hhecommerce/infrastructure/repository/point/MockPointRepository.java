package org.hhplus.hhecommerce.infrastructure.repository.point;

import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MockPointRepository implements PointRepository {

    private final Map<Long, Point> store = new ConcurrentHashMap<>();
    private final Map<Long, Point> userPointIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Point save(Point point) {
        if (point.getId() == null) {
            point.setId(idGenerator.getAndIncrement());
        }
        store.put(point.getId(), point);
        userPointIndex.put(point.getUser().getId(), point);
        return point;
    }

    @Override
    public Optional<Point> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Point> findByUserId(Long userId) {
        return Optional.ofNullable(userPointIndex.get(userId));
    }

    @Override
    public void delete(Point point) {
        store.remove(point.getId());
        userPointIndex.remove(point.getUser().getId());
    }
}
