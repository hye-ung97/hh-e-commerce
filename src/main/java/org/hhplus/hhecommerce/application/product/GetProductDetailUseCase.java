package org.hhplus.hhecommerce.application.product;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.product.ProductDetailResponse;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetProductDetailUseCase {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;

    @Cacheable(value = "products:detail", key = "#productId", sync = true)
    public ProductDetailResponse execute(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));

        List<ProductOption> options = productOptionRepository.findByProductId(productId);

        List<ProductDetailResponse.ProductOptionInfo> optionInfos = options.stream()
                .map(option -> new ProductDetailResponse.ProductOptionInfo(
                        option.getId(),
                        option.getOptionName(),
                        option.getOptionValue(),
                        option.getPrice(),
                        option.getStock()
                ))
                .collect(Collectors.toList());

        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getStatus().name(),
                optionInfos
        );
    }
}
