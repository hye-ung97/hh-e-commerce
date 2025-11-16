package org.hhplus.hhecommerce.domain.cart;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    List<Cart> findByUserId(Long userId);

    List<Cart> findByUserId(Long userId, Pageable pageable);

    int countByUserId(Long userId);

    Optional<Cart> findByUserIdAndProductOptionId(Long userId, Long productOptionId);

    void deleteAllByUserId(Long userId);
}
