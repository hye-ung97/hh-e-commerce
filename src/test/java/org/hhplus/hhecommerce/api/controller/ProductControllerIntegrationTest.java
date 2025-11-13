package org.hhplus.hhecommerce.api.controller;

import org.hhplus.hhecommerce.config.TestContainersConfig;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.infrastructure.repository.product.ProductRepository;
import org.hhplus.hhecommerce.infrastructure.repository.product.ProductOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
@DisplayName("ProductController 통합 테스트")
class ProductControllerIntegrationTest extends TestContainersConfig {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    private Product product1;
    private Product product2;
    private ProductOption option1;
    private ProductOption option2;

    @BeforeEach
    void setUp() {
        product1 = new Product("노트북", "고성능 노트북", "전자제품");
        product1 = productRepository.save(product1);

        product2 = new Product("마우스", "무선 마우스", "전자제품");
        product2 = productRepository.save(product2);

        option1 = new ProductOption(product1, "색상", "실버", 1500000, 100);
        option1 = productOptionRepository.save(option1);

        option2 = new ProductOption(product1, "색상", "블랙", 1600000, 50);
        option2 = productOptionRepository.save(option2);

        ProductOption mouseOption = new ProductOption(product2, "색상", "화이트", 50000, 200);
        productOptionRepository.save(mouseOption);
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공")
    void getProducts_success() throws Exception {
        // when & then
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.products[*].name", hasItem("노트북")))
                .andExpect(jsonPath("$.products[*].name", hasItem("마우스")))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("상품 목록 조회 - 페이징")
    void getProducts_withPagination() throws Exception {
        // given - 추가 상품 생성
        for (int i = 3; i <= 10; i++) {
            Product product = new Product("상품" + i, "설명" + i, "카테고리" + i);
            product = productRepository.save(product);
            ProductOption option = new ProductOption(product, "옵션", "값", 10000 * i, 100);
            productOptionRepository.save(option);
        }

        // when & then - 첫 페이지 (size=5)
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "5"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(5)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(5)))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(10)));

        // when & then - 두 번째 페이지
        mockMvc.perform(get("/api/products")
                        .param("page", "1")
                        .param("size", "5"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(5)))
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.size", is(5)));
    }

    @Test
    @DisplayName("상품 목록 조회 - 가격 및 재고 정보 포함")
    void getProducts_withPriceAndStock() throws Exception {
        // when & then
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].minPrice", everyItem(greaterThanOrEqualTo(0))))
                .andExpect(jsonPath("$.products[*].totalStock", everyItem(greaterThanOrEqualTo(0))))
                .andExpect(jsonPath("$.products[*].name", notNullValue()))
                .andExpect(jsonPath("$.products[*].category", notNullValue()));
    }

    @Test
    @DisplayName("상품 상세 조회 - 성공")
    void getProductDetail_success() throws Exception {
        // when & then
        mockMvc.perform(get("/api/products/{productId}", product1.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(product1.getId().intValue())))
                .andExpect(jsonPath("$.name", is("노트북")))
                .andExpect(jsonPath("$.category", is("전자제품")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.options", hasSize(2)))
                .andExpect(jsonPath("$.options[0].optionName", is("색상")))
                .andExpect(jsonPath("$.options[*].optionValue", hasItems("실버", "블랙")))
                .andExpect(jsonPath("$.options[*].price", hasItems(1500000, 1600000)))
                .andExpect(jsonPath("$.options[*].stock", hasItems(100, 50)));
    }

    @Test
    @DisplayName("상품 상세 조회 - 존재하지 않는 상품 (실패)")
    void getProductDetail_notFound() throws Exception {
        // given
        Long nonExistentProductId = 99999L;

        // when & then
        mockMvc.perform(get("/api/products/{productId}", nonExistentProductId))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("인기 상품 조회 - 성공")
    void getPopularProducts_success() throws Exception {
        // when & then
        mockMvc.perform(get("/api/products/popular"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.totalCount", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("인기 상품 조회 - 최대 5개만 반환")
    void getPopularProducts_maxFiveItems() throws Exception {
        // given - 추가 상품 생성 (총 10개 이상)
        for (int i = 3; i <= 10; i++) {
            Product product = new Product("인기상품" + i, "설명" + i, "전자제품");
            productRepository.save(product);
        }

        // when & then - 최대 5개만 반환되어야 함
        mockMvc.perform(get("/api/products/popular"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(lessThanOrEqualTo(5))))
                .andExpect(jsonPath("$.totalCount", lessThanOrEqualTo(5)));
    }

    @Test
    @DisplayName("상품 상세 조회 - 옵션이 없는 상품")
    void getProductDetail_noOptions() throws Exception {
        // given - 옵션이 없는 상품 생성
        Product productWithoutOptions = new Product("키보드", "기계식 키보드", "전자제품");
        productWithoutOptions = productRepository.save(productWithoutOptions);

        // when & then
        mockMvc.perform(get("/api/products/{productId}", productWithoutOptions.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(productWithoutOptions.getId().intValue())))
                .andExpect(jsonPath("$.name", is("키보드")))
                .andExpect(jsonPath("$.options", hasSize(0)));
    }

    @Test
    @DisplayName("상품 목록 조회 - 기본 페이징 파라미터")
    void getProducts_defaultPagination() throws Exception {
        // when & then - 기본값 page=0, size=20
        mockMvc.perform(get("/api/products"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.products", notNullValue()));
    }

    @Test
    @DisplayName("상품 목록 조회 - 카테고리별 상품 확인")
    void getProducts_checkCategory() throws Exception {
        // when & then
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].category", everyItem(notNullValue())))
                .andExpect(jsonPath("$.products[*].name", hasItem("노트북")))
                .andExpect(jsonPath("$.products[*].name", hasItem("마우스")));
    }

    @Test
    @DisplayName("상품 상세 조회 - 여러 옵션이 있는 상품")
    void getProductDetail_multipleOptions() throws Exception {
        // given - 여러 옵션을 가진 상품 생성
        Product multiOptionProduct = new Product("셔츠", "면 셔츠", "의류");
        multiOptionProduct = productRepository.save(multiOptionProduct);

        ProductOption sizeS = new ProductOption(multiOptionProduct, "사이즈", "S", 30000, 10);
        ProductOption sizeM = new ProductOption(multiOptionProduct, "사이즈", "M", 30000, 20);
        ProductOption sizeL = new ProductOption(multiOptionProduct, "사이즈", "L", 30000, 15);
        productOptionRepository.save(sizeS);
        productOptionRepository.save(sizeM);
        productOptionRepository.save(sizeL);

        // when & then
        mockMvc.perform(get("/api/products/{productId}", multiOptionProduct.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(multiOptionProduct.getId().intValue())))
                .andExpect(jsonPath("$.name", is("셔츠")))
                .andExpect(jsonPath("$.options", hasSize(3)))
                .andExpect(jsonPath("$.options[*].optionValue", hasItems("S", "M", "L")))
                .andExpect(jsonPath("$.options[*].stock", hasItems(10, 20, 15)));
    }

    @Test
    @DisplayName("상품 상태 확인 - ACTIVE 상태")
    void getProducts_activeStatus() throws Exception {
        // when & then
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].status", everyItem(is("ACTIVE"))));
    }

    @Test
    @DisplayName("인기 상품 조회 - 상품 정보 확인")
    void getPopularProducts_checkProductInfo() throws Exception {
        // when & then
        mockMvc.perform(get("/api/products/popular"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].productId", everyItem(notNullValue())))
                .andExpect(jsonPath("$.products[*].name", everyItem(notNullValue())))
                .andExpect(jsonPath("$.products[*].category", everyItem(notNullValue())))
                .andExpect(jsonPath("$.products[*].status", everyItem(notNullValue())))
                .andExpect(jsonPath("$.products[*].totalSales", everyItem(greaterThanOrEqualTo(0))));
    }
}
