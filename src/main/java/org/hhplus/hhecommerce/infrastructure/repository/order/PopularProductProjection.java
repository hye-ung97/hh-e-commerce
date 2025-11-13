package org.hhplus.hhecommerce.infrastructure.repository.order;

public interface PopularProductProjection {
    Long getProductId();
    String getProductName();
    String getCategory();
    String getStatus();
    Long getTotalSales();
}
