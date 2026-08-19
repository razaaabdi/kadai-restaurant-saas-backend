package com.restaurant.platform.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {
	List<OutboxEntity> findTop50ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(String status, Instant now);
}
