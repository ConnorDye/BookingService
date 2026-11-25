package com.connor.bookingservice.service;

import com.connor.bookingservice.model.Home;
import com.connor.bookingservice.model.Reservation;
import com.connor.bookingservice.model.Reservation.Status;
import com.connor.bookingservice.model.ReservationHold;
import com.connor.bookingservice.repository.HomeRepository;
import com.connor.bookingservice.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private final HomeRepository homeRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationHoldStore holdStore;

    public BookingService(HomeRepository homeRepository,
                          ReservationRepository reservationRepository,
                          ReservationHoldStore holdStore) {
        this.homeRepository = homeRepository;
        this.reservationRepository = reservationRepository;
        this.holdStore = holdStore;
    }

    // ---------------------------------------------------------------
    // Redis-backed cart flow
    // Ordering invariant: claim in Redis -> check DB -> (later) insert in DB
    // -> release Redis only after the DB commit.
    // ---------------------------------------------------------------

    /** Add to cart: claims the dates in Redis for 10 minutes. No DB write. */
    public ReservationHold holdReservation(Long homeId, String guestId,
                                           LocalDate checkIn, LocalDate checkOut) {
        validateDates(checkIn, checkOut);

        if (!homeRepository.existsById(homeId)) {
            throw new NotFoundException("Home " + homeId + " not found");
        }

        // 1. Claim in Redis first (throws BookingConflictException if held)
        ReservationHold hold = holdStore.tryAcquire(homeId, guestId, checkIn, checkOut);

        // 2. Then check confirmed bookings. Releasing on any failure avoids
        //    leaving orphaned holds if the DB call throws.
        try {
            if (reservationRepository.existsActiveOverlap(homeId, checkIn, checkOut)) {
                throw new BookingConflictException(
                        "Home " + homeId + " is already booked for " + checkIn + " to " + checkOut);
            }
        } catch (RuntimeException e) {
            holdStore.release(hold);
            throw e;
        }
        return hold;
    }

    /** Checkout: turns a hold into a CONFIRMED reservation. */
    @Transactional
    public Reservation confirmReservation(String guestId, Long homeId, LocalDate checkIn, LocalDate checkOut) {
        String holdId = ReservationHoldStore.holdId(homeId, checkIn, checkOut);
        ReservationHold hold = holdStore.getHold(holdId);                    // throws if expired

        if (!hold.guestId().equals(guestId)) {
            throw new AccessDeniedException("Not your hold");
        }

        Home home = homeRepository.findByIdForUpdate(hold.homeId())
                .orElseThrow(() -> new NotFoundException("Home " + hold.homeId() + " not found"));

        if (!holdStore.stillOwnsAllNights(hold)) {
            throw new HoldExpiredException("Hold " + holdId + " expired");
        }

        if (reservationRepository.existsActiveOverlap(hold.homeId(), hold.checkIn(), hold.checkOut())) {
            throw new BookingConflictException("Dates no longer available");
        }

        Reservation reservation = new Reservation(home, guestId, hold.checkIn(), hold.checkOut());
        reservation.setStatus(Status.CONFIRMED);
        Reservation saved = reservationRepository.save(reservation);

        // Release Redis only AFTER the DB commit succeeds
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                holdStore.release(hold);
            }
        });
        return saved;
    }

    /** Guest removes the item from their cart. */
    public void releaseHold(String holdId, String guestId) {
        ReservationHold hold = holdStore.getHold(holdId);
        if (!hold.guestId().equals(guestId)) {
            throw new AccessDeniedException("Not your hold");
        }
        holdStore.release(hold);
    }

    // ---------------------------------------------------------------
    // DB-only strategies (kept for the concurrency demo)
    // ---------------------------------------------------------------

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

    @Transactional
    public Reservation cancelReservation(Long reservationId) {
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