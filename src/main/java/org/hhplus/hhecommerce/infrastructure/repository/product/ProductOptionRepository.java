package org.hhplus.hhecommerce.infrastructure.repository.product;

import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
    List<ProductOption> findByProductId(Long productId);
}
