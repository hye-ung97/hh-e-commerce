package org.hhplus.hhecommerce.application.ranking;

import org.hhplus.hhecommerce.api.dto.product.RealtimeRankingResponse;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.hhplus.hhecommerce.domain.ranking.RankingEntry;
import org.hhplus.hhecommerce.domain.ranking.RankingRepository;
import org.hhplus.hhecommerce.domain.ranking.RankingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRealtimeRankingUseCaseTest {

    @Mock
    private RankingRepository rankingRepository;

    @Mock
    private ProductRepository productRepository;

    private GetRealtimeRankingUseCase getRealtimeRankingUseCase;

    @BeforeEach
    void setUp() {
        getRealtimeRankingUseCase = new GetRealtimeRankingUseCase(rankingRepository, productRepository);
    }

    @Test
    @DisplayName("일간 랭킹 TOP 10을 조회한다")
    void 일간_랭킹_TOP_10을_조회한다() {
        // Given
        List<RankingEntry> rankings = List.of(
                new RankingEntry(1L, 100.0, 1),
                new RankingEntry(2L, 80.0, 2),
                new RankingEntry(3L, 60.0, 3)
        );

        List<Product> products = List.of(
                createProduct(1L, "인기상품1"),
                createProduct(2L, "인기상품2"),
                createProduct(3L, "인기상품3")
        );

        when(rankingRepository.getTopRanking(RankingType.DAILY, 10)).thenReturn(rankings);
        when(productRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(products);

        // When
        RealtimeRankingResponse response = getRealtimeRankingUseCase.execute(RankingType.DAILY, 10);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.rankingType()).isEqualTo("DAILY");
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.rankings()).hasSize(3);
        assertThat(response.rankings().get(0).rank()).isEqualTo(1);
        assertThat(response.rankings().get(0).productName()).isEqualTo("인기상품1");
        assertThat(response.rankings().get(0).salesCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("주간 랭킹을 조회한다")
    void 주간_랭킹을_조회한다() {
        // Given
        List<RankingEntry> rankings = List.of(
                new RankingEntry(1L, 500.0, 1),
                new RankingEntry(2L, 300.0, 2)
        );

        List<Product> products = List.of(
                createProduct(1L, "주간인기1"),
                createProduct(2L, "주간인기2")
        );

        when(rankingRepository.getTopRanking(RankingType.WEEKLY, 10)).thenReturn(rankings);
        when(productRepository.findAllById(List.of(1L, 2L))).thenReturn(products);

        // When
        RealtimeRankingResponse response = getRealtimeRankingUseCase.execute(RankingType.WEEKLY, 10);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.rankingType()).isEqualTo("WEEKLY");
        assertThat(response.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("랭킹 데이터가 없으면 빈 목록을 반환한다")
    void 랭킹_데이터가_없으면_빈_목록을_반환한다() {
        // Given
        when(rankingRepository.getTopRanking(RankingType.DAILY, 10)).thenReturn(List.of());

        // When
        RealtimeRankingResponse response = getRealtimeRankingUseCase.execute(RankingType.DAILY, 10);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.rankings()).isEmpty();
        assertThat(response.totalCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("limit이 null이면 기본값 10을 사용한다")
    void limit이_null이면_기본값_10을_사용한다() {
        // Given
        when(rankingRepository.getTopRanking(RankingType.DAILY, 10)).thenReturn(List.of());

        // When
        RealtimeRankingResponse response = getRealtimeRankingUseCase.execute(RankingType.DAILY, null);

        // Then
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("limit이 50을 초과하면 50으로 제한된다")
    void limit이_50을_초과하면_50으로_제한된다() {
        // Given
        when(rankingRepository.getTopRanking(RankingType.DAILY, 50)).thenReturn(List.of());

        // When
        RealtimeRankingResponse response = getRealtimeRankingUseCase.execute(RankingType.DAILY, 100);

        // Then
        assertThat(response).isNotNull();
    }

    private Product createProduct(Long id, String name) {
        Product product = new Product(id, name, "설명", "전자제품", ProductStatus.ACTIVE);
        return product;
    }
}
