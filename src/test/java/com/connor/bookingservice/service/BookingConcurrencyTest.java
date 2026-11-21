package com.connor.bookingservice.service;

import com.connor.bookingservice.model.Home;
import com.connor.bookingservice.repository.HomeRepository;
import com.connor.bookingservice.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

// IMPORTANT: no @Transactional on this class. A test-managed transaction would hold
// uncommitted data the worker threads can't see, and would hide the real commit behavior.
@SpringBootTest
class BookingConcurrencyTest {

    private static final int THREADS = 20;

    @Autowired
    BookingService bookingService;
    @Autowired
    HomeRepository homeRepository;
    @Autowired
    ReservationRepository reservationRepository;

    private Long homeId;
    private final LocalDate checkIn = LocalDate.now().plusDays(10);
    private final LocalDate checkOut = LocalDate.now().plusDays(15);

    @BeforeEach
    void seed() {
        // save() commits immediately (no surrounding test transaction)
        homeId = homeRepository.save(
                new Home("Test House", "1 Test St", new BigDecimal("100.00"), 4)).getId();
    }

    @AfterEach
    void cleanup() {
        reservationRepository.deleteAll();
        homeRepository.deleteAll();
    }

    @Test
    void testSafeBookingWhenConcurrentReservationRequestsOccur() throws Exception {
        BookingResultTransactionCounter bookingResultTransactionCounter = runConcurrently(randomGuestIdentifier ->
                bookingService.createReservation(homeId, "guest-" + randomGuestIdentifier, checkIn, checkOut));

        System.out.println("SAFE   -> " + bookingResultTransactionCounter);

        assertEquals(1, bookingResultTransactionCounter.success.get(), "Exactly one booking should win");
        assertEquals(THREADS - 1, bookingResultTransactionCounter.conflict.get() + bookingResultTransactionCounter.other.get(),
                "Everyone else should be rejected");
        assertEquals(1, reservationRepository.findByHomeId(homeId).size(),
                "Database must contain exactly one reservation");
    }

    /**
     * Demonstration only: no lock, so double-booking is POSSIBLE. It's a race, so the outcome
     * varies per run and per database, which is why this asserts nothing. Run it a few times
     * and watch the printed numbers.
     */
    @Test
    void unsafeBooking_mayDoubleBook() throws Exception {
        BookingResultTransactionCounter bookingResultTransactionCounter = runConcurrently(i ->
                bookingService.createReservationUnsafe(homeId, "guest-" + i, checkIn, checkOut));

        System.out.println("UNSAFE -> " + bookingResultTransactionCounter
                + ", rows in DB = " + reservationRepository.findByHomeId(homeId).size());
    }


    /**
     *
     * @param success
     * @param conflict
     * @param other
     */
    private record BookingResultTransactionCounter(AtomicInteger success, AtomicInteger conflict, AtomicInteger other) {
        @Override
        public String toString() {
            return "success=" + success + ", conflict=" + conflict + ", other=" + other;
        }
    }

    /**
     * CountDownLatch start = new CountDownLatch(1); - this is the starting signal. All of our
     * worker threads warm up and reach this point, and our main test thread will eventually release this latch
     * Runs the same action on THREADS threads,
     * released at the same instant. */
    private BookingResultTransactionCounter runConcurrently(Consumer<Integer> action) throws Exception {
        BookingResultTransactionCounter bookingResultTransactionCounter = new BookingResultTransactionCounter(new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
        try (ExecutorService threadPool = Executors.newFixedThreadPool(THREADS)) {
            CountDownLatch ready = new CountDownLatch(THREADS);   // all threads are warmed up
            CountDownLatch start = new CountDownLatch(1);         // the starting signal
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < THREADS; i++) {
                final int randomGuestIdentifier = i; //this randomGuestIdentifier is appended to the reservation request
                futures.add(threadPool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();                            // wait until everyone is ready
                        action.accept(randomGuestIdentifier);
                        bookingResultTransactionCounter.success.incrementAndGet();
                    } catch (BookingConflictException e) {
                        bookingResultTransactionCounter.conflict.incrementAndGet();
                    } catch (Exception e) {
                        // lock timeout, constraint violation, etc.
                        bookingResultTransactionCounter.other.incrementAndGet();
                        Logger.getGlobal().log(Level.SEVERE, e.getClass().getSimpleName());
                    }
                }));
            }

            //assures all threads have reached the start.await() point and are "warmed up"
            ready.await();

            //fire all the threads
            start.countDown();

            //we already check the futures in the results, so no need to check the future results
            //the purpose of this is to wait for all futures to finish before shutting down
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }


            threadPool.shutdownNow();
        }
        return bookingResultTransactionCounter;
    }
}