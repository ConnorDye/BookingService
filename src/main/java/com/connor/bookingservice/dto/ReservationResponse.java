package com.connor.bookingservice.dto;

import com.connor.bookingservice.model.Reservation;

import java.time.Instant;
import java.time.LocalDate;

public record ReservationResponse(
        Long id,
        Long homeId,
        String guestId,
        LocalDate checkIn,
        LocalDate checkOut,
        String status,
        Instant createdAt) {

    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getHome().getId(), // getId() on a lazy proxy does not trigger a query
                r.getGuestId(),
                r.getCheckIn(),
                r.getCheckOut(),
                r.getStatus().name(),
                r.getCreatedAt());
    }
}
