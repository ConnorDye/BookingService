package com.connor.bookingservice.dto;

import com.connor.bookingservice.model.Home;

import java.math.BigDecimal;
import java.time.Instant;

public record HomeResponse(
        Long id,
        String name,
        String address,
        BigDecimal pricePerNight,
        int maxGuests,
        Instant createdAt) {

    public static HomeResponse from(Home home) {
        return new HomeResponse(
                home.getId(),
                home.getName(),
                home.getAddress(),
                home.getPricePerNight(),
                home.getMaxGuests(),
                home.getCreatedAt());
    }
}
