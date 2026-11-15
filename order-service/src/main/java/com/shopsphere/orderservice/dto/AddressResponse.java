package com.shopsphere.orderservice.dto;

public record AddressResponse(String fullName, String line1, String line2, String city, String state,
                              String postalCode, String country, String phone) {
}
