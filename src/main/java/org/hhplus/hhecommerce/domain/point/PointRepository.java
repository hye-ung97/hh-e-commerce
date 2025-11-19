package org.hhplus.hhecommerce.domain.point;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PointRepository extends JpaRepository<Point, Long> {
    Optional<Point> findByUserId(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Point p SET p.amount = p.amount + :chargeAmount, p.version = p.version + 1, p.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE p.userId = :userId AND p.amount + :chargeAmount <= 100000")
    int chargePoint(@Param("userId") Long userId, @Param("chargeAmount") int chargeAmount);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Point p SET p.amount = p.amount - :deductAmount, p.version = p.version + 1, p.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE p.userId = :userId AND p.amount >= :deductAmount")
    int deductPoint(@Param("userId") Long userId, @Param("deductAmount") int deductAmount);
}
