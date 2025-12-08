package org.hhplus.hhecommerce.infrastructure.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.ranking.RankingEntry;
import org.hhplus.hhecommerce.domain.ranking.RankingRepository;
import org.hhplus.hhecommerce.domain.ranking.RankingType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class RedisRankingRepository implements RankingRepository {

    private static final String DAILY_KEY_PREFIX = "ranking:daily:";
    private static final String WEEKLY_KEY_PREFIX = "ranking:weekly:";
    private static final String TIMESTAMP_SUFFIX = ":timestamp";

    private static final Duration DAILY_TTL = Duration.ofHours(25);
    private static final Duration WEEKLY_TTL = Duration.ofDays(8);

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> incrementScoreScript;

    public RedisRankingRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.incrementScoreScript = createIncrementScoreScript();
    }

    /**
     * 점수 증가, 타임스탬프 저장, TTL 설정을 원자적으로 수행하는 Lua 스크립트.
     * Sorted Set과 Hash의 생명주기를 동기화하여 메모리 누수를 방지합니다.
     */
    private DefaultRedisScript<Long> createIncrementScoreScript() {
        String script = """
            -- KEYS[1]: ranking sorted set key
            -- KEYS[2]: timestamp hash key
            -- ARGV[1]: productId
            -- ARGV[2]: score increment
            -- ARGV[3]: current timestamp
            -- ARGV[4]: TTL in seconds

            -- 1. 점수 증가 (Sorted Set)
            redis.call('ZINCRBY', KEYS[1], ARGV[2], ARGV[1])

            -- 2. 타임스탬프 저장 (Hash)
            redis.call('HSET', KEYS[2], ARGV[1], ARGV[3])

            -- 3. 두 키의 TTL을 동일하게 설정/갱신
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            redis.call('EXPIRE', KEYS[2], ARGV[4])

            return 1
            """;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    @Override
    public void incrementScore(RankingType type, Long productId, double score) {
        String rankingKey = generateRankingKey(type);
        String timestampKey = rankingKey + TIMESTAMP_SUFFIX;
        String productIdStr = productId.toString();

        Duration ttl = (type == RankingType.DAILY) ? DAILY_TTL : WEEKLY_TTL;
        long ttlSeconds = ttl.toSeconds();

        try {
            List<String> keys = Arrays.asList(rankingKey, timestampKey);
            redisTemplate.execute(
                    incrementScoreScript,
                    keys,
                    productIdStr,
                    String.valueOf(score),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(ttlSeconds)
            );

            log.debug("랭킹 점수 업데이트 - type: {}, productId: {}, score: +{}", type, productId, score);
        } catch (Exception e) {
            log.error("랭킹 점수 업데이트 실패 - type: {}, productId: {}, score: {}", type, productId, score, e);
            throw e;
        }
    }

    @Override
    public List<RankingEntry> getTopRanking(RankingType type, int limit) {
        String rankingKey = generateRankingKey(type);
        String timestampKey = rankingKey + TIMESTAMP_SUFFIX;

        int fetchLimit = Math.min(limit * 2, 100);

        Set<ZSetOperations.TypedTuple<String>> rankingSet =
                redisTemplate.opsForZSet().reverseRangeWithScores(rankingKey, 0, fetchLimit - 1);

        if (rankingSet == null || rankingSet.isEmpty()) {
            return List.of();
        }

        List<String> productIds = rankingSet.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .toList();

        List<Object> timestamps = redisTemplate.opsForHash().multiGet(timestampKey,
                productIds.stream().map(id -> (Object) id).toList());

        Map<String, Long> timestampMap = buildTimestampMap(productIds, timestamps);

        List<RankingEntry> sortedEntries = rankingSet.stream()
                .sorted(Comparator
                        .comparingDouble((ZSetOperations.TypedTuple<String> t) ->
                                t.getScore() != null ? t.getScore() : 0.0)
                        .reversed()
                        .thenComparing(t -> timestampMap.getOrDefault(t.getValue(), 0L), Comparator.reverseOrder()))
                .limit(limit)
                .map(tuple -> new RankingEntry(
                        Long.parseLong(tuple.getValue()),
                        tuple.getScore() != null ? tuple.getScore() : 0.0,
                        0
                ))
                .toList();

        List<RankingEntry> result = new ArrayList<>();
        for (int i = 0; i < sortedEntries.size(); i++) {
            RankingEntry entry = sortedEntries.get(i);
            result.add(new RankingEntry(entry.productId(), entry.score(), i + 1));
        }

        return result;
    }

    @Override
    public Long getRank(RankingType type, Long productId) {
        String rankingKey = generateRankingKey(type);
        Long rank = redisTemplate.opsForZSet().reverseRank(rankingKey, productId.toString());
        return rank != null ? rank + 1 : null;  // 0-based → 1-based
    }

    @Override
    public Double getScore(RankingType type, Long productId) {
        String rankingKey = generateRankingKey(type);
        return redisTemplate.opsForZSet().score(rankingKey, productId.toString());
    }

    private String generateRankingKey(RankingType type) {
        LocalDate today = LocalDate.now();
        return switch (type) {
            case DAILY -> DAILY_KEY_PREFIX + today;
            case WEEKLY -> WEEKLY_KEY_PREFIX + getYearWeek(today);
        };
    }

    private String getYearWeek(LocalDate date) {
        WeekFields weekFields = WeekFields.of(DayOfWeek.MONDAY, 4);
        int year = date.getYear();
        int week = date.get(weekFields.weekOfWeekBasedYear());
        return String.format("%d:%02d", year, week);
    }

    private Map<String, Long> buildTimestampMap(List<String> productIds, List<Object> timestamps) {
        return java.util.stream.IntStream.range(0, productIds.size())
                .boxed()
                .collect(Collectors.toMap(
                        productIds::get,
                        i -> {
                            Object ts = timestamps.get(i);
                            if (ts == null) return 0L;
                            try {
                                return Long.parseLong(ts.toString());
                            } catch (NumberFormatException e) {
                                return 0L;
                            }
                        }
                ));
    }
}
