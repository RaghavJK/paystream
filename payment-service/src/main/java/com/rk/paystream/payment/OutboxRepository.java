package com.rk.paystream.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Locks the oldest unsent rows. SKIP LOCKED lets several instances of this service run the
     * relay at once: each takes a different batch instead of waiting on the other's locks.
     * Must be called inside a transaction; the locks are held until it ends.
     */
    @Query(value = "SELECT * FROM outbox WHERE sent_at IS NULL ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("limit") int limit);
}
