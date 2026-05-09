package com.example.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddressRequest {

    @NotBlank(message = "Street line 1 is required")
    @Size(max = 255)
    private String streetLine1;

    @Size(max = 255)
    private String streetLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100)
    private String state;

    @NotBlank(message = "Postcode is required")
    @Size(max = 20)
    private String postcode;

    @NotBlank(message = "Country is required")
    @Size(max = 100)
    private String country;

    private boolean isDefault;

    public String getStreetLine1()        { return streetLine1; }
    public void setStreetLine1(String s)  { this.streetLine1 = s; }
    public String getStreetLine2()        { return streetLine2; }
    public void setStreetLine2(String s)  { this.streetLine2 = s; }
    public String getCity()               { return city; }
    public void setCity(String c)         { this.city = c; }
    public String getState()              { return state; }
    public void setState(String s)        { this.state = s; }
    public String getPostcode()           { return postcode; }
    public void setPostcode(String p)     { this.postcode = p; }
    public String getCountry()            { return country; }
    public void setCountry(String c)      { this.country = c; }
    public boolean isDefault()            { return isDefault; }
    public void setDefault(boolean d)     { this.isDefault = d; }
}
