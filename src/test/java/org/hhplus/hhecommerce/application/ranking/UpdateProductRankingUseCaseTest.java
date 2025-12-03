package org.hhplus.hhecommerce.application.ranking;

import org.hhplus.hhecommerce.domain.ranking.RankingRepository;
import org.hhplus.hhecommerce.domain.ranking.RankingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateProductRankingUseCaseTest {

    @Mock
    private RankingRepository rankingRepository;

    private UpdateProductRankingUseCase updateProductRankingUseCase;

    @BeforeEach
    void setUp() {
        updateProductRankingUseCase = new UpdateProductRankingUseCase(rankingRepository);
    }

    @Test
    @DisplayName("주문 수량만큼 일간/주간 랭킹 점수가 증가한다")
    void 주문_수량만큼_일간_주간_랭킹_점수가_증가한다() {
        // Given
        Long productId = 1L;
        int quantity = 5;

        // When
        updateProductRankingUseCase.execute(productId, quantity);

        // Then
        verify(rankingRepository).incrementScore(RankingType.DAILY, productId, quantity);
        verify(rankingRepository).incrementScore(RankingType.WEEKLY, productId, quantity);
    }

    @Test
    @DisplayName("상품 ID가 null이면 랭킹 업데이트를 하지 않는다")
    void 상품_ID가_null이면_랭킹_업데이트를_하지_않는다() {
        // When
        updateProductRankingUseCase.execute(null, 5);

        // Then
        verifyNoInteractions(rankingRepository);
    }

    @Test
    @DisplayName("수량이 0 이하이면 랭킹 업데이트를 하지 않는다")
    void 수량이_0_이하이면_랭킹_업데이트를_하지_않는다() {
        // When
        updateProductRankingUseCase.execute(1L, 0);
        updateProductRankingUseCase.execute(1L, -1);

        // Then
        verifyNoInteractions(rankingRepository);
    }

    @Test
    @DisplayName("여러 번 호출하면 각각 랭킹이 업데이트된다")
    void 여러_번_호출하면_각각_랭킹이_업데이트된다() {
        // Given
        Long productId1 = 1L;
        Long productId2 = 2L;

        // When
        updateProductRankingUseCase.execute(productId1, 3);
        updateProductRankingUseCase.execute(productId2, 7);

        // Then
        verify(rankingRepository).incrementScore(RankingType.DAILY, productId1, 3);
        verify(rankingRepository).incrementScore(RankingType.WEEKLY, productId1, 3);
        verify(rankingRepository).incrementScore(RankingType.DAILY, productId2, 7);
        verify(rankingRepository).incrementScore(RankingType.WEEKLY, productId2, 7);
    }
}
