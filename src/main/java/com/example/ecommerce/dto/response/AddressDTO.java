package com.example.ecommerce.dto.response;

import com.example.ecommerce.domain.Address;

public class AddressDTO {

    private Long id;
    private String streetLine1;
    private String streetLine2;
    private String city;
    private String state;
    private String postcode;
    private String country;
    private boolean isDefault;

    public AddressDTO() {}

    public static AddressDTO from(Address a) {
        AddressDTO dto = new AddressDTO();
        dto.id          = a.getId();
        dto.streetLine1 = a.getStreetLine1();
        dto.streetLine2 = a.getStreetLine2();
        dto.city        = a.getCity();
        dto.state       = a.getState();
        dto.postcode    = a.getPostcode();
        dto.country     = a.getCountry();
        dto.isDefault   = a.isDefault();
        return dto;
    }

    public Long getId()              { return id; }
    public String getStreetLine1()   { return streetLine1; }
    public String getStreetLine2()   { return streetLine2; }
    public String getCity()          { return city; }
    public String getState()         { return state; }
    public String getPostcode()      { return postcode; }
    public String getCountry()       { return country; }
    public boolean isDefault()       { return isDefault; }
}
