package com.connor.bookingservice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record HoldRequest(
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut
) {}
