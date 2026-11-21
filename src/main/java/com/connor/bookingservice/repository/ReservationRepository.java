package com.connor.bookingservice.repository;

import com.connor.bookingservice.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.connor.bookingservice.model.Reservation.Status;


import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // CRUD (save, findById, findAll, deleteById, ...) is inherited from JpaRepository.

    // Derived queries: Spring generates the SQL from the method name.
    List<Reservation> findByHomeId(Long homeId);

    List<Reservation> findByGuestId(String guestId);

    List<Reservation> findByHomeIdAndStatus(Long homeId, Status status);

    /**
     * True if any non-excluded reservation for this home overlaps [checkIn, checkOut).
     * Overlap rule for half-open ranges: existing.checkIn < new.checkOut AND existing.checkOut > new.checkIn
     */
    @Query("""
            select count(r) > 0
            from Reservation r
            where r.home.id = :homeId
              and r.status <> :excludedStatus
              and r.checkIn < :checkOut
              and r.checkOut > :checkIn
            """)
    boolean existsOverlapping(@Param("homeId") Long homeId,
                              @Param("checkIn") LocalDate checkIn,
                              @Param("checkOut") LocalDate checkOut,
                              @Param("excludedStatus") Status excludedStatus);

    /** Convenience wrapper: cancelled reservations never block new bookings. */
    default boolean existsActiveOverlap(Long homeId, LocalDate checkIn, LocalDate checkOut) {
        return existsOverlapping(homeId, checkIn, checkOut, Status.CANCELLED);
    }

    /** Same overlap check but returns the conflicting rows (useful for error messages/debugging). */
    @Query("""
            select r
            from Reservation r
            where r.home.id = :homeId
              and r.status <> :excludedStatus
              and r.checkIn < :checkOut
              and r.checkOut > :checkIn
            """)
    List<Reservation> findOverlapping(@Param("homeId") Long homeId,
                                      @Param("checkIn") LocalDate checkIn,
                                      @Param("checkOut") LocalDate checkOut,
                                      @Param("excludedStatus") Status excludedStatus);
}