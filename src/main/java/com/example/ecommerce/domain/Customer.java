package com.example.ecommerce.domain;

import com.example.ecommerce.domain.enums.Role;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "customers",
       uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@EntityListeners(AuditingEntityListener.class)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "customer_roles",
                     joinColumns = @JoinColumn(name = "customer_id"))
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "customer",
               cascade = CascadeType.ALL,
               orphanRemoval = true)
    private List<Address> addresses = new ArrayList<>();

    @OneToOne(mappedBy = "customer",
              cascade = CascadeType.ALL,
              fetch = FetchType.LAZY)
    private Cart cart;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    protected Customer() {}

    public Customer(String firstName, String lastName,
                    String email, String passwordHash) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles.add(Role.CUSTOMER);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String n) { this.firstName = n; }
    public String getLastName() { return lastName; }
    public void setLastName(String n) { this.lastName = n; }
    public String getEmail() { return email; }
    public void setEmail(String e) { this.email = e; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String h) { this.passwordHash = h; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> r) { this.roles = r; }
    public List<Address> getAddresses() { return addresses; }
    public Cart getCart() { return cart; }
    public void setCart(Cart cart) { this.cart = cart; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
