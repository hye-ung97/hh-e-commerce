package org.hhplus.hhecommerce.application.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.ranking.RankingRepository;
import org.hhplus.hhecommerce.domain.ranking.RankingType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProductRankingUseCase {

    private final RankingRepository rankingRepository;

    public void execute(Long productId, int quantity) {
        if (productId == null || quantity <= 0) {
            log.warn("잘못된 랭킹 업데이트 요청 - productId: {}, quantity: {}", productId, quantity);
            return;
        }

        rankingRepository.incrementScore(RankingType.DAILY, productId, quantity);

        rankingRepository.incrementScore(RankingType.WEEKLY, productId, quantity);

        log.info("실시간 랭킹 업데이트 완료 - productId: {}, quantity: {}", productId, quantity);
    }
}
