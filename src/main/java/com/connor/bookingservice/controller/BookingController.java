package com.connor.bookingservice.controller;


import com.connor.bookingservice.dto.CreateReservationRequest;
import com.connor.bookingservice.dto.ReservationResponse;
import com.connor.bookingservice.model.Reservation;
import com.connor.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/bookings/v1")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }



    /** Safe path: pessimistic lock on the home row. */
    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest req) {
        Reservation saved = bookingService.createReservation(
                req.homeId(), req.guestId(), req.checkIn(), req.checkOut());
        return responseEntityFromReservationResponse(saved);
    }

    /** No locking. Exists only so you can demonstrate double-booking under load. */
    @PostMapping("/reservations/unsafe")
    //    public ResponseEntity<ReservationResponse> createUnsafe(@Valid @RequestBody CreateReservationRequest req) {
    //        Reservation saved = bookingService.createReservationUnsafe(
    //                req.homeId(), req.guestId(), req.checkIn(), req.checkOut());
    //        return responseEntityFromReservationResponse(saved);
    //    }

    @GetMapping("/reservations/{id}")
    public ReservationResponse get(@PathVariable Long id) {
        return ReservationResponse.from(bookingService.getReservation(id));
    }

    @GetMapping("/reservations/homes/{homeId}")
    public List<ReservationResponse> listForHome(@PathVariable Long homeId) {
        return bookingService.getReservationsForHome(homeId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @GetMapping("/reservations")
    public List<ReservationResponse> listForGuest(@RequestParam String guestId) {
        return bookingService.getReservationsForGuest(guestId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @PostMapping("/reservations/{id}/confirm")
    public ReservationResponse confirm(@PathVariable Long id) {
        return ReservationResponse.from(bookingService.confirm(id));
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id) {
        return ReservationResponse.from(bookingService.cancel(id));
    }



    private ResponseEntity<ReservationResponse> responseEntityFromReservationResponse(Reservation saved) {
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/bookings/v1/reservations/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(ReservationResponse.from(saved));
    }
}