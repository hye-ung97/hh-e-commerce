package org.hhplus.hhecommerce.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueRequest;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResultRecord;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResultRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueResultService {

    private final CouponIssueResultRepository couponIssueResultRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveResult(CouponIssueRequest request, CouponIssueStatus status, String message) {
        CouponIssueResultRecord result = new CouponIssueResultRecord(
                request.requestId(),
                request.couponId(),
                request.userId(),
                status,
                message,
                request.requestedAt()
        );
        couponIssueResultRepository.save(result);
        log.info("[CouponIssueResultService] 발급 결과 저장 - requestId: {}, status: {}, message: {}",
                request.requestId(), status, message);
    }

    @Transactional(readOnly = true)
    public boolean existsByRequestId(String requestId) {
        return couponIssueResultRepository.existsByRequestId(requestId);
    }
}
