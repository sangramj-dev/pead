package org.pead.earnings.repository;

import org.pead.earnings.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(String status);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = 'PUBLISHED', o.publishedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    void markPublished(@Param("id") Long id);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = 'FAILED', o.errorMessage = :error, o.retryCount = o.retryCount + 1 WHERE o.id = :id")
    void markFailed(@Param("id") Long id, @Param("error") String error);
}
