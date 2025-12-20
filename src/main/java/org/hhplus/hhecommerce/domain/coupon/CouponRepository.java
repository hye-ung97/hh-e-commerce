package org.hhplus.hhecommerce.domain.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Query("SELECT c FROM Coupon c WHERE c.startAt <= :now AND c.endAt >= :now AND (c.totalQuantity - c.issuedQuantity) > 0")
    List<Coupon> findAvailableCoupons(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Coupon c WHERE c.startAt <= :now AND c.endAt >= :now AND (c.totalQuantity - c.issuedQuantity) > 0")
    int countAvailableCoupons(@Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :couponId")
    Optional<Coupon> findByIdWithLock(@Param("couponId") Long couponId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + 1 " +
           "WHERE c.id = :couponId AND c.issuedQuantity < c.totalQuantity")
    int increaseIssuedQuantity(@Param("couponId") Long couponId);
}
