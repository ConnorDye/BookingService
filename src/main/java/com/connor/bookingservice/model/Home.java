package com.connor.bookingservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "homes")
@Setter
public class Home {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(name = "price_per_night", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "max_guests", nullable = false)
    private int maxGuests;

    // Optimistic locking: Hibernate adds "WHERE version = ?" to updates and
    // throws ObjectOptimisticLockingFailureException if another tx changed the row.
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected Home() {
        // required by JPA
    }

    public Home(String name, String address, BigDecimal pricePerNight, int maxGuests) {
        this.name = name;
        this.address = address;
        this.pricePerNight = pricePerNight;
        this.maxGuests = maxGuests;
    }

}