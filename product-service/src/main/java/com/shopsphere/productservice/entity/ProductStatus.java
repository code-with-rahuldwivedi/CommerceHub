package com.shopsphere.productservice.entity;

/**
 * ACTIVE: visible in the catalog. INACTIVE: hidden (unpublished) but editable.
 * ARCHIVED: soft-deleted. Rows are never physically deleted because orders reference product ids.
 */
public enum ProductStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
