package org.hhplus.hhecommerce.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductOptionRepository {

    ProductOption save(ProductOption productOption);

    Optional<ProductOption> findById(Long id);

    List<ProductOption> findByProductId(Long productId);

    List<ProductOption> findAll();

    void delete(ProductOption productOption);
}
