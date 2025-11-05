package org.hhplus.hhecommerce.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();

    List<Product> findAll(int page, int size);

    int countAll();

    List<Product> findByCategory(String category);

    List<Product> findByStatus(ProductStatus status);

    void delete(Product product);
}
