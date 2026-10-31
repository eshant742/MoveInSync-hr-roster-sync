package com.moveinsync.roster.repository;

import com.moveinsync.roster.domain.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link OutboxEvent} entity operations.
 *
 * <p>Implements the Transactional Outbox pattern by persisting domain events
 * within the same transaction as the aggregate change, then publishing them
 * asynchronously via a polling publisher.</p>
 *
 * <p>This ensures at-least-once delivery semantics for downstream consumers
 * without requiring distributed transactions.</p>
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Retrieves unpublished events in creation order, limited by page size.
     *
     * <p>The polling publisher uses this to fetch the next batch of events
     * to dispatch to downstream systems.</p>
     *
     * @param pageable pagination parameters controlling batch size
     * @return list of unpublished outbox events ordered by creation time
     */
    List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc(Pageable pageable);

    /**
     * Marks a single outbox event as published and records the publication timestamp.
     *
     * <p>Called after successful delivery to the downstream system. This is
     * an idempotent operation — re-marking an already-published event has
     * no adverse effect since the WHERE clause is on the primary key.</p>
     *
     * @param id the outbox event primary key
     * @return the number of rows updated (0 or 1)
     */
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.published = true, o.publishedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    int markPublished(@Param("id") Long id);

    /**
     * Counts the number of events still awaiting publication.
     *
     * <p>Used as a Micrometer gauge to monitor outbox backlog and
     * detect publishing delays or downstream issues.</p>
     *
     * @return the count of unpublished events
     */
    long countByPublishedFalse();
}
