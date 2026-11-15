package com.shopsphere.orderservice.client;

import java.util.Collection;
import java.util.Map;

public interface CatalogGateway {

    Map<Long, CatalogProduct> findActiveProducts(Collection<Long> productIds);
}
