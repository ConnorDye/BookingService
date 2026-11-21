package com.connor.bookingservice.dto;

import jakarta.validation.constraints.NotBlank;
import org.antlr.v4.runtime.misc.NotNull;

import java.time.LocalDate;

public record CreateReservationRequest(
        @NotNull Long homeId,
        @NotBlank String guestId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut) {
}
