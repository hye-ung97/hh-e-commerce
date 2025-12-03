package org.hhplus.hhecommerce.application.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.product.RealtimeRankingResponse;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.ranking.RankingEntry;
import org.hhplus.hhecommerce.domain.ranking.RankingRepository;
import org.hhplus.hhecommerce.domain.ranking.RankingType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetRealtimeRankingUseCase {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final RankingRepository rankingRepository;
    private final ProductRepository productRepository;

    public RealtimeRankingResponse execute(RankingType type, Integer limit) {
        int actualLimit = resolveLimit(limit);

        List<RankingEntry> rankings = rankingRepository.getTopRanking(type, actualLimit);

        if (rankings.isEmpty()) {
            log.debug("랭킹 데이터 없음 - type: {}", type);
            return new RealtimeRankingResponse(List.of(), type.name(), 0);
        }

        List<Long> productIds = rankings.stream()
                .map(RankingEntry::productId)
                .toList();

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<RealtimeRankingResponse.RankingProduct> rankingProducts = rankings.stream()
                .map(entry -> {
                    Product product = productMap.get(entry.productId());
                    if (product == null) {
                        log.warn("랭킹에 존재하지 않는 상품 - productId: {}", entry.productId());
                        return null;
                    }
                    return new RealtimeRankingResponse.RankingProduct(
                            entry.rank(),
                            product.getId(),
                            product.getName(),
                            product.getCategory(),
                            product.getStatus().name(),
                            (int) entry.score()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        log.debug("실시간 랭킹 조회 완료 - type: {}, count: {}", type, rankingProducts.size());
        return new RealtimeRankingResponse(rankingProducts, type.name(), rankingProducts.size());
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
