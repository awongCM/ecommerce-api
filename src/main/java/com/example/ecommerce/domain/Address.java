package com.example.ecommerce.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private String streetLine1;
    private String streetLine2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String postcode;

    @Column(nullable = false)
    private String country;

    private boolean isDefault;

    protected Address() {}

    public Address(Customer customer, String streetLine1, String city,
                   String state, String postcode, String country) {
        this.customer = customer;
        this.streetLine1 = streetLine1;
        this.city = city;
        this.state = state;
        this.postcode = postcode;
        this.country = country;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getStreetLine1() { return streetLine1; }
    public String getStreetLine2() { return streetLine2; }
    public void setStreetLine2(String s) { this.streetLine2 = s; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostcode() { return postcode; }
    public String getCountry() { return country; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean d) { this.isDefault = d; }
}
