package org.hhplus.hhecommerce.domain.ranking;

public record RankingEntry(
        Long productId,
        double score,
        long rank
) {
}
