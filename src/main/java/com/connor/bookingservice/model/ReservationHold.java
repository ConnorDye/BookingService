package com.connor.bookingservice.model;

import java.time.LocalDate;

public record ReservationHold(String holdId, Long homeId, String guestId,
                              LocalDate checkIn, LocalDate checkOut) {}
