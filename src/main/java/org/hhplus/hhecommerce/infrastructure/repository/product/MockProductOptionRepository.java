package org.hhplus.hhecommerce.infrastructure.repository.product;

import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class MockProductOptionRepository implements ProductOptionRepository {

    private final Map<Long, ProductOption> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public ProductOption save(ProductOption productOption) {
        if (productOption.getId() == null) {
            productOption.setId(idGenerator.getAndIncrement());
        }
        store.put(productOption.getId(), productOption);
        return productOption;
    }

    @Override
    public Optional<ProductOption> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ProductOption> findByProductId(Long productId) {
        return store.values().stream()
                .filter(option -> option.getProduct().getId().equals(productId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductOption> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void delete(ProductOption productOption) {
        store.remove(productOption.getId());
    }
}
