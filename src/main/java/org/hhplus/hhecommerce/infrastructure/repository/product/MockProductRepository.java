package org.hhplus.hhecommerce.infrastructure.repository.product;

import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class MockProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockProductRepository() {
        // 초기 상품 데이터
        save(new Product("노트북", "고성능 노트북", "전자제품"));
        save(new Product("무선 키보드", "블루투스 무선 키보드", "전자제품"));
        save(new Product("마우스", "게이밍 마우스", "전자제품"));
        save(new Product("모니터", "27인치 4K 모니터", "전자제품"));
        save(new Product("헤드셋", "노이즈 캔슬링 헤드셋", "전자제품"));
    }

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            product.setId(idGenerator.getAndIncrement());
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Product> findAll(int page, int size) {
        if (page < 0 || size <= 0) {
            return new ArrayList<>();
        }
        return store.values().stream()
                .sorted(Comparator.comparing(Product::getId))
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    @Override
    public int countAll() {
        return store.size();
    }

    @Override
    public List<Product> findByCategory(String category) {
        return store.values().stream()
                .filter(product -> product.getCategory().equals(category))
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findByStatus(org.hhplus.hhecommerce.domain.product.ProductStatus status) {
        return store.values().stream()
                .filter(product -> product.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Product product) {
        store.remove(product.getId());
    }
}
