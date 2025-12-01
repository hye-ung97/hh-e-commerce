package org.hhplus.hhecommerce.infrastructure.ranking;

import org.hhplus.hhecommerce.config.TestContainersConfig;
import org.hhplus.hhecommerce.domain.ranking.RankingEntry;
import org.hhplus.hhecommerce.domain.ranking.RankingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRankingRepositoryTest extends TestContainersConfig {

    @Autowired
    private RedisRankingRepository redisRankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // 테스트 전 랭킹 키 삭제
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("상품 점수를 증가시킨다")
    void incrementScore_success() {
        // given
        Long productId = 1L;
        double score = 5.0;

        // when
        redisRankingRepository.incrementScore(RankingType.DAILY, productId, score);

        // then
        Double actualScore = redisRankingRepository.getScore(RankingType.DAILY, productId);
        assertThat(actualScore).isEqualTo(5.0);
    }

    @Test
    @DisplayName("여러 번 점수를 증가시키면 누적된다")
    void incrementScore_accumulates() {
        // given
        Long productId = 1L;

        // when
        redisRankingRepository.incrementScore(RankingType.DAILY, productId, 3.0);
        redisRankingRepository.incrementScore(RankingType.DAILY, productId, 7.0);

        // then
        Double actualScore = redisRankingRepository.getScore(RankingType.DAILY, productId);
        assertThat(actualScore).isEqualTo(10.0);
    }

    @Test
    @DisplayName("TOP N 랭킹을 점수 내림차순으로 조회한다")
    void getTopRanking_orderedByScoreDesc() {
        // given
        redisRankingRepository.incrementScore(RankingType.DAILY, 1L, 10.0);
        redisRankingRepository.incrementScore(RankingType.DAILY, 2L, 30.0);
        redisRankingRepository.incrementScore(RankingType.DAILY, 3L, 20.0);

        // when
        List<RankingEntry> rankings = redisRankingRepository.getTopRanking(RankingType.DAILY, 10);

        // then
        assertThat(rankings).hasSize(3);
        assertThat(rankings.get(0).productId()).isEqualTo(2L);
        assertThat(rankings.get(0).score()).isEqualTo(30.0);
        assertThat(rankings.get(0).rank()).isEqualTo(1);
        assertThat(rankings.get(1).productId()).isEqualTo(3L);
        assertThat(rankings.get(1).score()).isEqualTo(20.0);
        assertThat(rankings.get(1).rank()).isEqualTo(2);
        assertThat(rankings.get(2).productId()).isEqualTo(1L);
        assertThat(rankings.get(2).score()).isEqualTo(10.0);
        assertThat(rankings.get(2).rank()).isEqualTo(3);
    }

    @Test
    @DisplayName("limit 만큼만 랭킹을 조회한다")
    void getTopRanking_respectsLimit() {
        // given
        for (int i = 1; i <= 10; i++) {
            redisRankingRepository.incrementScore(RankingType.DAILY, (long) i, i * 10.0);
        }

        // when
        List<RankingEntry> rankings = redisRankingRepository.getTopRanking(RankingType.DAILY, 5);

        // then
        assertThat(rankings).hasSize(5);
        assertThat(rankings.get(0).productId()).isEqualTo(10L);
        assertThat(rankings.get(4).productId()).isEqualTo(6L);
    }

    @Test
    @DisplayName("랭킹 데이터가 없으면 빈 리스트를 반환한다")
    void getTopRanking_emptyWhenNoData() {
        // when
        List<RankingEntry> rankings = redisRankingRepository.getTopRanking(RankingType.DAILY, 10);

        // then
        assertThat(rankings).isEmpty();
    }

    @Test
    @DisplayName("특정 상품의 순위를 조회한다")
    void getRank_success() {
        // given
        redisRankingRepository.incrementScore(RankingType.DAILY, 1L, 10.0);
        redisRankingRepository.incrementScore(RankingType.DAILY, 2L, 30.0);
        redisRankingRepository.incrementScore(RankingType.DAILY, 3L, 20.0);

        // when
        Long rank1 = redisRankingRepository.getRank(RankingType.DAILY, 2L);
        Long rank2 = redisRankingRepository.getRank(RankingType.DAILY, 3L);
        Long rank3 = redisRankingRepository.getRank(RankingType.DAILY, 1L);

        // then
        assertThat(rank1).isEqualTo(1L); // 점수 30, 1등
        assertThat(rank2).isEqualTo(2L); // 점수 20, 2등
        assertThat(rank3).isEqualTo(3L); // 점수 10, 3등
    }

    @Test
    @DisplayName("랭킹에 없는 상품의 순위는 null을 반환한다")
    void getRank_nullWhenNotExists() {
        // when
        Long rank = redisRankingRepository.getRank(RankingType.DAILY, 999L);

        // then
        assertThat(rank).isNull();
    }

    @Test
    @DisplayName("일간과 주간 랭킹은 별도로 관리된다")
    void dailyAndWeeklyRankingsAreSeparate() {
        // given
        redisRankingRepository.incrementScore(RankingType.DAILY, 1L, 10.0);
        redisRankingRepository.incrementScore(RankingType.WEEKLY, 1L, 50.0);

        // when
        Double dailyScore = redisRankingRepository.getScore(RankingType.DAILY, 1L);
        Double weeklyScore = redisRankingRepository.getScore(RankingType.WEEKLY, 1L);

        // then
        assertThat(dailyScore).isEqualTo(10.0);
        assertThat(weeklyScore).isEqualTo(50.0);
    }

    @Test
    @DisplayName("동점자 처리: 최근 주문이 더 높은 순위를 가진다")
    void getTopRanking_tieBreakByTimestamp() throws InterruptedException {
        // given - 같은 점수, 다른 시간에 등록
        redisRankingRepository.incrementScore(RankingType.DAILY, 1L, 10.0);
        Thread.sleep(10); // 타임스탬프 차이를 위해 대기
        redisRankingRepository.incrementScore(RankingType.DAILY, 2L, 10.0);

        // when
        List<RankingEntry> rankings = redisRankingRepository.getTopRanking(RankingType.DAILY, 10);

        // then
        assertThat(rankings).hasSize(2);
        // 나중에 등록된 상품(2L)이 더 높은 순위
        assertThat(rankings.get(0).productId()).isEqualTo(2L);
        assertThat(rankings.get(1).productId()).isEqualTo(1L);
    }
}
