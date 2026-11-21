package com.connor.bookingservice.service;


import com.connor.bookingservice.model.Home;
import com.connor.bookingservice.model.Reservation;
import com.connor.bookingservice.model.Reservation.Status;
import com.connor.bookingservice.repository.HomeRepository;
import com.connor.bookingservice.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private final HomeRepository homeRepository;
    private final ReservationRepository reservationRepository;

    public BookingService(HomeRepository homeRepository,
                          ReservationRepository reservationRepository) {
        this.homeRepository = homeRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * Strategy 1: pessimistic row lock on the home.
     *
     * Order matters:
     *   1. lock the home row (SELECT ... FOR UPDATE), other bookings for this home queue here
     *   2. check for overlapping reservations (now safe: nobody else can insert for this home)
     *   3. insert
     * The lock is released when this transaction commits or rolls back.
     */
    @Transactional
    public Reservation createReservation(Long homeId, String guestId,
                                         LocalDate checkIn, LocalDate checkOut) {
        validateDates(checkIn, checkOut);

        Home home = homeRepository.findByIdForUpdate(homeId)
                .orElseThrow(() -> new NotFoundException("Home " + homeId + " not found"));

        if (reservationRepository.existsActiveOverlap(homeId, checkIn, checkOut)) {
            throw new BookingConflictException(
                    "Home " + homeId + " is already booked for " + checkIn + " to " + checkOut);
        }

        return reservationRepository.save(new Reservation(home, guestId, checkIn, checkOut));
    }

    /**
     * Same flow WITHOUT any lock. Use this to demonstrate the double-booking race in your
     * concurrency test, then compare against createReservation().
     */
    @Transactional
    public Reservation createReservationUnsafe(Long homeId, String guestId,
                                               LocalDate checkIn, LocalDate checkOut) {
        validateDates(checkIn, checkOut);

        Home home = homeRepository.findById(homeId)
                .orElseThrow(() -> new NotFoundException("Home " + homeId + " not found"));

        if (reservationRepository.existsActiveOverlap(homeId, checkIn, checkOut)) {
            throw new BookingConflictException(
                    "Home " + homeId + " is already booked for " + checkIn + " to " + checkOut);
        }

        return reservationRepository.save(new Reservation(home, guestId, checkIn, checkOut));
    }

    /** PENDING -> CONFIRMED. @Version on Reservation guards against racing with a cancel. */
    @Transactional
    public Reservation confirm(Long reservationId) {
        Reservation reservation = getReservation(reservationId);

        if (reservation.getStatus() != Reservation.Status.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING reservations can be confirmed, current status: " + reservation.getStatus());
        }
        reservation.setStatus(Status.CONFIRMED);
        return reservationRepository.save(reservation);
    }

    /** Idempotent: cancelling an already-cancelled reservation is a no-op. */
    @Transactional
    public Reservation cancel(Long reservationId) {
        Reservation reservation = getReservation(reservationId);

        if (reservation.getStatus() == Status.CANCELLED) {
            return reservation;
        }
        reservation.setStatus(Status.CANCELLED);
        return reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public Reservation getReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation " + reservationId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Reservation> getReservationsForHome(Long homeId) {
        return reservationRepository.findByHomeId(homeId);
    }

    @Transactional(readOnly = true)
    public List<Reservation> getReservationsForGuest(String guestId) {
        return reservationRepository.findByGuestId(guestId);
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("checkIn and checkOut are required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("checkIn cannot be in the past");
        }
    }


}