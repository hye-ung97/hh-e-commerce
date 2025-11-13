package org.hhplus.hhecommerce.infrastructure.repository.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PopularProductDto {
    private Long productId;
    private String productName;
    private String category;
    private String status;
    private Long totalSales;
}
