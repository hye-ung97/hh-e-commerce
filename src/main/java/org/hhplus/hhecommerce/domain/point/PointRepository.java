package org.hhplus.hhecommerce.domain.point;

import java.util.Optional;

public interface PointRepository {

    Point save(Point point);

    Optional<Point> findById(Long id);

    Optional<Point> findByUserId(Long userId);

    void delete(Point point);
}
