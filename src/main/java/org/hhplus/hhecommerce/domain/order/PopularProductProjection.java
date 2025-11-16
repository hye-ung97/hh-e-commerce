package org.hhplus.hhecommerce.domain.order;

public interface PopularProductProjection {
    Long getProductId();
    String getProductName();
    String getCategory();
    String getStatus();
    Long getTotalSales();
}
