package org.hhplus.hhecommerce.domain.ranking;

import java.util.List;

public interface RankingRepository {

    void incrementScore(RankingType type, Long productId, double score);

    List<RankingEntry> getTopRanking(RankingType type, int limit);

    Long getRank(RankingType type, Long productId);

    Double getScore(RankingType type, Long productId);
}
