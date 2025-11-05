package org.hhplus.hhecommerce.domain.cart;

import java.util.List;
import java.util.Optional;

public interface CartRepository {
    Cart save(Cart cart);

    Optional<Cart> findById(Long id);

    List<Cart> findByUserId(Long userId);

    List<Cart> findByUserId(Long userId, int page, int size);

    int countByUserId(Long userId);

    Optional<Cart> findByUserIdAndProductOptionId(Long userId, Long productOptionId);

    void delete(Cart cart);

    void deleteAllByUserId(Long userId);
}
