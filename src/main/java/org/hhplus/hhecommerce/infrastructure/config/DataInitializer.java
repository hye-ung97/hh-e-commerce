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

        User user1 = userRepository.findById(1L).orElseThrow();
        User user2 = userRepository.findById(2L).orElseThrow();
        User user3 = userRepository.findById(3L).orElseThrow();

        Point point1 = new Point(user1);
        point1.charge(1000000);
        pointRepository.save(point1);
        log.info("User1 포인트 생성: {}P", point1.getAmount());

        Point point2 = new Point(user2);
        point2.charge(500000);
        pointRepository.save(point2);
        log.info("User2 포인트 생성: {}P", point2.getAmount());

        Point point3 = new Point(user3);
        point3.charge(2000000);
        pointRepository.save(point3);
        log.info("User3 포인트 생성: {}P", point3.getAmount());

        Product product1 = productRepository.findById(1L).orElseThrow();
        ProductOption option1_1 = new ProductOption(product1, "색상", "실버", 0, 5);
        ProductOption option1_2 = new ProductOption(product1, "용량", "512GB", 200000, 5);
        productOptionRepository.save(option1_1);
        productOptionRepository.save(option1_2);
        log.info("노트북 옵션 생성: 2개");

        Product product2 = productRepository.findById(2L).orElseThrow();
        ProductOption option2_1 = new ProductOption(product2, "색상", "블랙", 0, 10);
        ProductOption option2_2 = new ProductOption(product2, "색상", "화이트", 5000, 10);
        productOptionRepository.save(option2_1);
        productOptionRepository.save(option2_2);
        log.info("무선 키보드 옵션 생성: 2개");

        log.info("========== 초기 데이터 생성 완료 ==========");
    }
}
