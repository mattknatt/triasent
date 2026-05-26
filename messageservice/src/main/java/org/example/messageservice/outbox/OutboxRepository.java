package org.example.messageservice.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Oldest-first batch of not-yet-published events. The pessimistic lock plus
     * {@code SKIP LOCKED} (lock timeout -2) lets multiple service replicas poll
     * concurrently without ever handing the same row to two relays.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.createdAt asc")
    List<OutboxEvent> findUnpublishedBatch(Pageable pageable);
}
