package com.restaurant.outlet.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface DiningTableRepository extends JpaRepository<TableEntity, UUID> {
	List<TableEntity> findByOutletIdAndDeletedFalseOrderByCodeAsc(UUID outletId);
	List<TableEntity> findByAreaId(UUID areaId);

	@Query("select t.status from TableEntity t where t.id = :id")
	String statusOf(@Param("id") UUID id);

	@Modifying(clearAutomatically = false, flushAutomatically = false)
	@Query(value = "update tables set status = :status where id = :id", nativeQuery = true)
	int updateStatus(@Param("id") UUID id, @Param("status") String status);

	boolean existsByOutletIdAndCodeIgnoreCaseAndDeletedFalse(UUID outletId, String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from TableEntity t where t.id = :id")
	Optional<TableEntity> findByIdForUpdate(@Param("id") UUID id);
}
