package com.shopsphere.orderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// Shipping address SNAPSHOT: later changes in the customer's profile never alter a placed order.
@Embeddable
public class Address {

    @Column(name = "ship_full_name", length = 100)
    private String fullName;

    @Column(name = "ship_line1", length = 200)
    private String line1;

    @Column(name = "ship_line2", length = 200)
    private String line2;

    @Column(name = "ship_city", length = 100)
    private String city;

    @Column(name = "ship_state", length = 100)
    private String state;

    @Column(name = "ship_postal_code", length = 20)
    private String postalCode;

    @Column(name = "ship_country", length = 2)
    private String country;

    @Column(name = "ship_phone", length = 20)
    private String phone;

    public Address() {
    }

    public Address(String fullName, String line1, String line2, String city, String state,
                   String postalCode, String country, String phone) {
        this.fullName = fullName;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
        this.phone = phone;
    }

    public String getFullName() { return fullName; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public String getPhone() { return phone; }
}