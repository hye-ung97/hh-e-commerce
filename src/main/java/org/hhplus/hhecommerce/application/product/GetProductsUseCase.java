package org.hhplus.hhecommerce.application.product;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.product.ProductListResponse;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetProductsUseCase {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;

    public ProductListResponse execute(int page, int size) {
        List<Product> products = productRepository.findAll(page, size);
        int totalCount = productRepository.countAll();

        List<ProductListResponse.ProductSummary> productSummaries = products.stream()
                .map(product -> {
                    List<ProductOption> options = productOptionRepository.findByProductId(product.getId());
                    int totalStock = options.stream()
                            .mapToInt(ProductOption::getStock)
                            .sum();

                    int minPrice = options.stream()
                            .mapToInt(ProductOption::getPrice)
                            .min()
                            .orElse(0);

                    return new ProductListResponse.ProductSummary(
                            product.getId(),
                            product.getName(),
                            minPrice,
                            totalStock,
                            product.getCategory(),
                            product.getStatus().name()
                    );
                })
                .collect(Collectors.toList());

        return ProductListResponse.builder()
                .products(productSummaries)
                .page(page)
                .size(size)
                .total(totalCount)
                .build();
    }
}
