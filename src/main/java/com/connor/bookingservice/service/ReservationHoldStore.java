package com.connor.bookingservice.service;

import com.connor.bookingservice.model.ReservationHold;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Redis-only storage for cart holds. Knows nothing about JPA or the DB. */
@Component
public class ReservationHoldStore {

    static final Duration HOLD_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    public ReservationHoldStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // KEYS[1]   = hold metadata hash
    // KEYS[2..] = one key per night
    // ARGV[1] = guestId, ARGV[2] = ttl seconds,
    // ARGV[3] = homeId, ARGV[4] = checkIn, ARGV[5] = checkOut
    private static final RedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
        for i = 2, #KEYS do
          if redis.call('EXISTS', KEYS[i]) == 1 then return 0 end
        end
        for i = 2, #KEYS do
          redis.call('SET', KEYS[i], ARGV[1], 'EX', ARGV[2])
        end
        redis.call('HSET', KEYS[1],
                   'homeId',   ARGV[3],
                   'guestId',  ARGV[1],
                   'checkIn',  ARGV[4],
                   'checkOut', ARGV[5])
        redis.call('EXPIRE', KEYS[1], ARGV[2])
        return 1
        """, Long.class);

    // KEYS[1] = hold hash, KEYS[2..] = nights, ARGV[1] = guestId
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
        local n = 0
        for i = 2, #KEYS do
          if redis.call('GET', KEYS[i]) == ARGV[1] then
            redis.call('DEL', KEYS[i])
            n = n + 1
          end
        end
        if redis.call('HGET', KEYS[1], 'guestId') == ARGV[1] then
          redis.call('DEL', KEYS[1])
        end
        return n
        """, Long.class);


    /**
     * Atomically claims every night and stores the cart item.
     * Throws BookingConflictException if any night is already held.
     */
    public ReservationHold tryAcquire(Long homeId, String guestId,
                                      LocalDate checkIn, LocalDate checkOut) {

        String holdId = holdId(homeId, checkIn, checkOut);
        List<String> keys = new ArrayList<>();
        keys.add(getHashKeyForBooking(homeId, holdId));                        // KEYS[1] in redis script
        keys.addAll(getHashKeysForAllNights(homeId, checkIn, checkOut));       // KEYS[2..] in redis script

        Long ok = redis.execute(ACQUIRE, keys,
                guestId,
                String.valueOf(HOLD_TTL.toSeconds()),
                homeId.toString(),
                checkIn.toString(),
                checkOut.toString());

        if (ok == null || ok == 0L) {
            // Same guest retrying the same dates: their hold already exists
            Object owner = redis.opsForHash().get(getHashKeyForBooking(homeId, holdId), "guestId");
            if (guestId.equals(owner)) {
                return new ReservationHold(holdId, homeId, guestId, checkIn, checkOut);
            }
            throw new BookingConflictException(
                    "Home " + homeId + " is currently held by another guest for those dates");
        }

        return new ReservationHold(holdId, homeId, guestId, checkIn, checkOut);
    }

    public ReservationHold getHold(String holdId) {
        Long homeId = homeIdFrom(holdId);
        Map<Object, Object> m = redis.opsForHash().entries(getHashKeyForBooking(homeId, holdId));
        if (m.isEmpty()) {
            throw new HoldExpiredException("Hold " + holdId + " expired or doesn't exist");
        }
        return new ReservationHold(holdId,
                Long.valueOf((String) m.get("homeId")),
                (String) m.get("guestId"),
                LocalDate.parse((String) m.get("checkIn")),
                LocalDate.parse((String) m.get("checkOut")));
    }

    public void release(ReservationHold h) {
        List<String> keys = new ArrayList<>();
        keys.add(getHashKeyForBooking(h.homeId(), h.holdId()));
        keys.addAll(getHashKeysForAllNights(h.homeId(), h.checkIn(), h.checkOut()));
        redis.execute(RELEASE, keys, h.guestId());
    }

    /** True only if every night key still belongs to this hold (i.e. it didn't expire). */
    public boolean stillOwnsAllNights(ReservationHold h) {
        List<String> values = redis.opsForValue()
                .multiGet(getHashKeysForAllNights(h.homeId(), h.checkIn(), h.checkOut()));
        return values != null && values.stream().allMatch(h.guestId()::equals);
    }


    static List<String> getHashKeysForAllNights(Long homeId, LocalDate checkIn, LocalDate checkOut) {
        return checkIn.datesUntil(checkOut)                       // checkOut exclusive
                .map(d -> "hold:{home:" + homeId + "}:" + d)
                .toList();
    }

    /** Same {home:N} hash tag as the night keys so they share a cluster slot. */
    static String getHashKeyForBooking(Long homeId, String holdId) {
        return "hold:{home:" + homeId + "}:" + holdId;
    }

    /** holdId looks like "42_2026-12-20_2026-12-27" */
    private static Long homeIdFrom(String holdId) {
        return Long.valueOf(holdId.substring(0, holdId.indexOf('_')));
    }

    static String holdId(Long homeId, LocalDate checkIn, LocalDate checkOut) {
        return homeId + "_" + checkIn + "_" + checkOut;   // 42_2026-12-20_2026-12-27
    }

    static String holdKey(Long homeId, LocalDate checkIn, LocalDate checkOut) {
        return "hold:{homeIdentifier: " + homeId + "}: " + checkIn + "_" + checkOut;
    }
}
