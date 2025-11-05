package org.hhplus.hhecommerce.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hhplus.hhecommerce.api.dto.cart.AddCartRequest;
import org.hhplus.hhecommerce.api.dto.cart.UpdateCartRequest;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("CartController 통합 테스트")
class CartControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    private Long userId;
    private Product product;
    private ProductOption productOption;

    @BeforeEach
    void setUp() {
        userId = 1L;

        // 기존 장바구니 데이터 정리
        cartRepository.deleteAllByUserId(userId);

        // 테스트 상품 생성
        product = new Product("테스트 노트북", "고성능 노트북", "전자제품");
        product = productRepository.save(product);

        // 테스트 상품 옵션 생성
        productOption = new ProductOption(product, "색상", "실버", 1500000, 100);
        productOption = productOptionRepository.save(productOption);
    }

    @Test
    @DisplayName("장바구니 조회 - 빈 장바구니")
    void getCart_emptyCart() throws Exception {
        // when & then
        mockMvc.perform(get("/api/carts")
                        .param("userId", userId.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalCount", is(0)))
                .andExpect(jsonPath("$.totalAmount", is(0)));
    }

    @Test
    @DisplayName("장바구니 조회 - 항목이 있는 경우")
    void getCart_withItems() throws Exception {
        // given
        Cart cart = new Cart(userId, productOption.getId(), 2);
        cartRepository.save(cart);

        // when & then
        mockMvc.perform(get("/api/carts")
                        .param("userId", userId.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].userId", is(userId.intValue())))
                .andExpect(jsonPath("$.items[0].productName", is("테스트 노트북")))
                .andExpect(jsonPath("$.items[0].quantity", is(2)))
                .andExpect(jsonPath("$.items[0].price", is(1500000)))
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.totalAmount", is(3000000)));
    }

    @Test
    @DisplayName("장바구니에 상품 추가 - 새로운 상품")
    void addToCart_newProduct() throws Exception {
        // given
        AddCartRequest request = new AddCartRequest(productOption.getId(), 1);

        // when & then
        mockMvc.perform(post("/api/carts")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userId.intValue())))
                .andExpect(jsonPath("$.productOptionId", is(productOption.getId().intValue())))
                .andExpect(jsonPath("$.productName", is("테스트 노트북")))
                .andExpect(jsonPath("$.quantity", is(1)))
                .andExpect(jsonPath("$.price", is(1500000)))
                .andExpect(jsonPath("$.totalPrice", is(1500000)));
    }

    @Test
    @DisplayName("장바구니에 상품 추가 - 이미 존재하는 상품 (수량 증가)")
    void addToCart_existingProduct() throws Exception {
        // given
        Cart existingCart = new Cart(userId, productOption.getId(), 1);
        cartRepository.save(existingCart);

        AddCartRequest request = new AddCartRequest(productOption.getId(), 2);

        // when & then
        mockMvc.perform(post("/api/carts")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userId.intValue())))
                .andExpect(jsonPath("$.productOptionId", is(productOption.getId().intValue())))
                .andExpect(jsonPath("$.quantity", is(3))) // 1 + 2 = 3
                .andExpect(jsonPath("$.totalPrice", is(4500000))); // 1500000 * 3
    }

    @Test
    @DisplayName("장바구니 수량 변경 - 성공")
    void updateCart_success() throws Exception {
        // given
        Cart cart = new Cart(userId, productOption.getId(), 1);
        Cart savedCart = cartRepository.save(cart);

        UpdateCartRequest request = new UpdateCartRequest(5);

        // when & then
        mockMvc.perform(patch("/api/carts/{cartId}", savedCart.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedCart.getId().intValue())))
                .andExpect(jsonPath("$.quantity", is(5)))
                .andExpect(jsonPath("$.totalPrice", is(7500000))); // 1500000 * 5
    }

    @Test
    @DisplayName("장바구니 수량 변경 - 0 이하로 변경 시도 (실패)")
    void updateCart_invalidQuantity() throws Exception {
        // given
        Cart cart = new Cart(userId, productOption.getId(), 1);
        Cart savedCart = cartRepository.save(cart);

        UpdateCartRequest request = new UpdateCartRequest(0);

        // when & then
        mockMvc.perform(patch("/api/carts/{cartId}", savedCart.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("장바구니 상품 삭제 - 성공")
    void deleteCart_success() throws Exception {
        // given
        Cart cart = new Cart(userId, productOption.getId(), 1);
        Cart savedCart = cartRepository.save(cart);

        // when & then
        mockMvc.perform(delete("/api/carts/{cartId}", savedCart.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedCart.getId().intValue())))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("장바구니 상품 삭제 - 존재하지 않는 장바구니 (실패)")
    void deleteCart_notFound() throws Exception {
        // given
        Long nonExistentCartId = 99999L;

        // when & then
        mockMvc.perform(delete("/api/carts/{cartId}", nonExistentCartId))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("장바구니 페이징 조회 - 여러 항목")
    void getCart_withPagination() throws Exception {
        // given - 3개의 상품 옵션과 장바구니 항목 생성
        ProductOption option1 = new ProductOption(product, "색상", "블랙", 1600000, 50);
        ProductOption option2 = new ProductOption(product, "색상", "화이트", 1700000, 30);
        productOptionRepository.save(option1);
        productOptionRepository.save(option2);

        cartRepository.save(new Cart(userId, productOption.getId(), 1));
        cartRepository.save(new Cart(userId, option1.getId(), 2));
        cartRepository.save(new Cart(userId, option2.getId(), 1));

        // when & then - 첫 페이지 (size=2)
        mockMvc.perform(get("/api/carts")
                        .param("userId", userId.toString())
                        .param("page", "0")
                        .param("size", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalCount", is(3)));

        // when & then - 두 번째 페이지
        mockMvc.perform(get("/api/carts")
                        .param("userId", userId.toString())
                        .param("page", "1")
                        .param("size", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalCount", is(3)));
    }

    @Test
    @DisplayName("장바구니 추가 - 존재하지 않는 상품 옵션 (실패)")
    void addToCart_productOptionNotFound() throws Exception {
        // given
        Long nonExistentOptionId = 99999L;
        AddCartRequest request = new AddCartRequest(nonExistentOptionId, 1);

        // when & then
        mockMvc.perform(post("/api/carts")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("장바구니 추가 - 잘못된 수량 (0 이하)")
    void addToCart_invalidQuantity() throws Exception {
        // given
        AddCartRequest request = new AddCartRequest(productOption.getId(), 0);

        // when & then
        mockMvc.perform(post("/api/carts")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }
}
