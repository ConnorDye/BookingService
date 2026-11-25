package com.connor.bookingservice.controller;


import com.connor.bookingservice.dto.CreateReservationRequest;
import com.connor.bookingservice.dto.HoldRequest;
import com.connor.bookingservice.dto.HoldResponse;
import com.connor.bookingservice.dto.ReservationResponse;
import com.connor.bookingservice.model.Reservation;
import com.connor.bookingservice.model.ReservationHold;
import com.connor.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
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
    //    @PostMapping("/reservations/unsafe")
    //        public ResponseEntity<ReservationResponse> createUnsafe(@Valid @RequestBody CreateReservationRequest req) {
    //            Reservation saved = bookingService.createReservationUnsafe(
    //                    req.homeId(), req.guestId(), req.checkIn(), req.checkOut());
    //            return responseEntityFromReservationResponse(saved);
    //        }

    @GetMapping("/reservations/{id}")
    public ReservationResponse getReservation(@PathVariable Long id) {
        return ReservationResponse.from(bookingService.getReservation(id));
    }

    @GetMapping("/reservations/homes/{homeId}")
    public List<ReservationResponse> getReservationsForHome(@PathVariable Long homeId) {
        return bookingService.getReservationsForHome(homeId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @GetMapping("/reservations")
    public List<ReservationResponse> getReservationsForGuest(@RequestParam String guestId) {
        return bookingService.getReservationsForGuest(guestId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @PostMapping("/{homeId}/hold")
    public ResponseEntity<HoldResponse> holdReservation(
            @PathVariable Long homeId,
            @RequestHeader("X-Guest-Id") @NotBlank String guestId,
            @Valid @RequestBody HoldRequest request) {

        ReservationHold hold = bookingService.holdReservation(
                homeId, guestId, request.checkIn(), request.checkOut());

        return ResponseEntity.status(HttpStatus.CREATED).body(HoldResponse.from(hold));
    }

    @PostMapping("/holds/{homeId}/confirm")
    public ResponseEntity<ReservationResponse> confirmReservationn(
            @PathVariable Long homeId,
            @RequestHeader("X-Guest-Id") @NotBlank String guestId,
            @Valid @RequestBody HoldRequest request) {
        Reservation saved = bookingService.confirmReservation(guestId, homeId, request.checkIn(), request.checkOut());
        return responseEntityFromReservationResponse(saved);
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancelReservation(@PathVariable Long id) {
        return ReservationResponse.from(bookingService.cancelReservation(id));
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