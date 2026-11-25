package com.connor.bookingservice.dto;

import com.connor.bookingservice.model.ReservationHold;

import java.time.LocalDate;

public record HoldResponse(
        String holdId,
        Long homeId,
        String guestId,
        LocalDate checkIn,
        LocalDate checkOut
) {
    public static HoldResponse from(ReservationHold hold) {
        return new HoldResponse(
                hold.holdId(),
                hold.homeId(),
                hold.guestId(),
                hold.checkIn(),
                hold.checkOut()
        );
    }
}