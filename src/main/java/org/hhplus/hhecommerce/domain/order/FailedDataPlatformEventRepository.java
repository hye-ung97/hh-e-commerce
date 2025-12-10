package org.hhplus.hhecommerce.domain.order;

import org.hhplus.hhecommerce.domain.order.FailedDataPlatformEvent.RetryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FailedDataPlatformEventRepository extends JpaRepository<FailedDataPlatformEvent, Long> {

    List<FailedDataPlatformEvent> findByStatus(RetryStatus status);

    @Query("SELECT f FROM FailedDataPlatformEvent f " +
           "WHERE f.status = 'PENDING' AND f.retryCount < :maxRetryCount " +
           "ORDER BY f.createdAt ASC")
    List<FailedDataPlatformEvent> findPendingForRetry(@Param("maxRetryCount") int maxRetryCount,
                                                       Pageable pageable);

    boolean existsByOrderIdAndStatus(Long orderId, RetryStatus status);

    @Modifying
    @Query("DELETE FROM FailedDataPlatformEvent f " +
           "WHERE f.status IN ('COMPLETED', 'FAILED') AND f.completedAt < :before")
    int deleteCompletedBefore(@Param("before") LocalDateTime before);

    long countByStatus(RetryStatus status);
}
