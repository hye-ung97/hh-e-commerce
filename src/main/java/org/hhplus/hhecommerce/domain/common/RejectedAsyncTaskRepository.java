package org.hhplus.hhecommerce.domain.common;

import org.hhplus.hhecommerce.domain.common.RejectedAsyncTask.RetryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RejectedAsyncTaskRepository extends JpaRepository<RejectedAsyncTask, Long> {

    List<RejectedAsyncTask> findByStatus(RetryStatus status);

    List<RejectedAsyncTask> findByTaskTypeAndStatus(String taskType, RetryStatus status);

    @Query("SELECT r FROM RejectedAsyncTask r " +
           "WHERE r.status = 'PENDING' AND r.retryCount < :maxRetryCount " +
           "ORDER BY r.createdAt ASC")
    List<RejectedAsyncTask> findPendingForRetry(@Param("maxRetryCount") int maxRetryCount,
                                                 Pageable pageable);

    @Query("SELECT r FROM RejectedAsyncTask r " +
           "WHERE r.taskType = :taskType AND r.status = 'PENDING' AND r.retryCount < :maxRetryCount " +
           "ORDER BY r.createdAt ASC")
    List<RejectedAsyncTask> findPendingForRetryByTaskType(@Param("taskType") String taskType,
                                                           @Param("maxRetryCount") int maxRetryCount,
                                                           Pageable pageable);

    @Modifying
    @Query("DELETE FROM RejectedAsyncTask r " +
           "WHERE r.status IN ('COMPLETED', 'FAILED') AND r.completedAt < :before")
    int deleteCompletedBefore(@Param("before") LocalDateTime before);

    long countByStatus(RetryStatus status);

    long countByTaskTypeAndStatus(String taskType, RetryStatus status);
}
