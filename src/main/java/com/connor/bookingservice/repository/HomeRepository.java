package com.connor.bookingservice.repository;

import com.connor.bookingservice.model.Home;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 *     ... extends JpaRepository already provides CRUD for free:
 *     save(home)          -> INSERT (new) or UPDATE (existing)
 *     indById(id)         -> Optional<Home>
 *     findAll()           -> List<Home>
 *     existsById(id)
 *     deleteById(id) / delete(home)
 *     count()
 */

@Repository
public interface HomeRepository extends JpaRepository<Home, Long> {


    /**
     * Loads the home with SELECT ... FOR UPDATE. Other transactions trying to lock the
     * same home block until this transaction commits or rolls back.
     * Must be called inside a @Transactional method, or the lock is released immediately.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Don't wait forever: fail after 3s (value in milliseconds)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select h from Home h where h.id = :id")
    Optional<Home> findByIdForUpdate(@Param("id") Long id);
}
