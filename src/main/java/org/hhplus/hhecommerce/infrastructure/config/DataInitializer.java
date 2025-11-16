package org.hhplus.hhecommerce.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 초기 데이터를 생성합니다.
 * 테스트 환경에서는 실행되지 않습니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.data-initializer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PointRepository pointRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;

    @Override
    public void run(String... args) {
        log.info("========== 초기 데이터 생성 시작 ==========");

        // 이미 데이터가 존재하면 초기화 건너뛰기
        if (userRepository.count() > 0) {
            log.info("초기 데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        // 사용자 생성
        User user1 = userRepository.save(new User("테스트유저1", "user1@test.com"));
        User user2 = userRepository.save(new User("테스트유저2", "user2@test.com"));
        User user3 = userRepository.save(new User("테스트유저3", "user3@test.com"));
        log.info("사용자 생성: 3명");

        // 포인트 생성
        Point point1 = new Point(user1);
        point1.charge(100000);
        pointRepository.save(point1);
        log.info("User1 포인트 생성: {}P", point1.getAmount());

        Point point2 = new Point(user2);
        point2.charge(50000);
        pointRepository.save(point2);
        log.info("User2 포인트 생성: {}P", point2.getAmount());

        Point point3 = new Point(user3);
        point3.charge(80000);
        pointRepository.save(point3);
        log.info("User3 포인트 생성: {}P", point3.getAmount());

        // 상품 생성
        Product product1 = productRepository.save(new Product("노트북", "고성능 노트북", "전자기기"));
        Product product2 = productRepository.save(new Product("무선 키보드", "기계식 무선 키보드", "전자기기"));
        log.info("상품 생성: 2개");

        // 상품 옵션 생성
        ProductOption option1_1 = new ProductOption(product1, "색상", "실버", 0, 5);
        ProductOption option1_2 = new ProductOption(product1, "용량", "512GB", 200000, 5);
        productOptionRepository.save(option1_1);
        productOptionRepository.save(option1_2);
        log.info("노트북 옵션 생성: 2개");

        ProductOption option2_1 = new ProductOption(product2, "색상", "블랙", 0, 10);
        ProductOption option2_2 = new ProductOption(product2, "색상", "화이트", 5000, 10);
        productOptionRepository.save(option2_1);
        productOptionRepository.save(option2_2);
        log.info("무선 키보드 옵션 생성: 2개");

        log.info("========== 초기 데이터 생성 완료 ==========");
    }
}
