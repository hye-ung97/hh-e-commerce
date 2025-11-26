package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final RedissonClient redissonClient;

    @Transactional
    public IssueCouponResponse execute(Long userId, Long couponId) {
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            log.warn("User {} already issued coupon {} (pre-check)", userId, couponId);
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }

        String lockKey = "coupon:issue:" + couponId + ":" + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(10, 5, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn("Failed to acquire lock for user {} and coupon {}", userId, couponId);
                throw new CouponException(CouponErrorCode.COUPON_ISSUE_TIMEOUT);
            }

            boolean alreadyIssued = userCouponRepository.existsByUserIdAndCouponId(userId, couponId);
            if (alreadyIssued) {
                log.warn("User {} already issued coupon {} (post-lock check)", userId, couponId);
                throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
            }

            Coupon coupon = couponRepository.findByIdWithLock(couponId)
                    .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

            if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                log.warn("Coupon {} sold out (issued: {}, total: {})",
                        couponId, coupon.getIssuedQuantity(), coupon.getTotalQuantity());
                throw new CouponException(CouponErrorCode.COUPON_OUT_OF_STOCK);
            }

            coupon.issue();
            couponRepository.save(coupon);

            UserCoupon userCoupon = new UserCoupon(userId, couponId, LocalDateTime.now().plusDays(30));
            userCouponRepository.save(userCoupon);

            log.info("Coupon {} issued to user {} (issued: {}/{})",
                    couponId, userId, coupon.getIssuedQuantity(), coupon.getTotalQuantity());

            return new IssueCouponResponse(
                    userCoupon.getId(),
                    userId,
                    coupon.getId(),
                    coupon.getName(),
                    coupon.getDiscountType().name(),
                    coupon.getDiscountValue(),
                    coupon.getMinOrderAmount(),
                    false,
                    userCoupon.getCreatedAt(),
                    userCoupon.getExpiredAt(),
                    "쿠폰이 발급되었습니다"
            );

        } catch (CouponException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for user {} and coupon {}", userId, couponId, e);
            throw new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED);
        } catch (Exception e) {
            log.error("Unexpected error during coupon issuance for user {} and coupon {}", userId, couponId, e);
            throw new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released for user {} and coupon {}", userId, couponId);
            }
        }
    }
}
