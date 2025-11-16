package org.hhplus.hhecommerce.domain.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);


    @Query(value = """
        SELECT
            p.id as productId,
            p.name as productName,
            p.category as category,
            p.status as status,
            sales.totalSales
        FROM (
            SELECT po.product_id, SUM(oi.quantity) as totalSales
            FROM order_item oi
            INNER JOIN product_option po ON oi.product_option_id = po.id
            WHERE oi.order_id IN (
                SELECT id FROM `order`
                WHERE created_at >= :startDate
            )
            GROUP BY po.product_id
            ORDER BY totalSales DESC
            LIMIT 5
        ) sales
        JOIN product p ON p.id = sales.product_id
        """, nativeQuery = true)
    List<PopularProductProjection> findTopSellingProducts(@Param("startDate") LocalDateTime startDate);
}
