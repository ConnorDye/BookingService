package com.connor.bookingservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "reservations",
        indexes = {
                // Speeds up the overlap check: WHERE home_id = ? AND check_in < ? AND check_out > ?
                @Index(name = "idx_reservation_home_dates", columnList = "home_id, check_in, check_out")
        }
)
public class Reservation {

    public enum Status {
        PENDING,    // holds the dates briefly (useful for payment flows / lock TTL practice)
        CONFIRMED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_id", nullable = false)
    private Home home;


    @Column(name = "guest_id", nullable = false)
    private String guestId;

    // Half-open interval [checkIn, checkOut): checkOut day is NOT occupied,
    // so back-to-back stays (out on the 5th, in on the 5th) don't conflict.
    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected Reservation() {
        // required by JPA
    }

    public Reservation(Home home, String guestId, LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
        this.home = home;
        this.guestId = guestId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    /** True if this reservation's dates overlap the given range (half-open intervals). */
    public boolean overlaps(LocalDate otherIn, LocalDate otherOut) {
        return checkIn.isBefore(otherOut) && checkOut.isAfter(otherIn);
    }

}