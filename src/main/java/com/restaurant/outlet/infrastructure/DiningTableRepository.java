package com.restaurant.outlet.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DiningTableRepository extends JpaRepository<TableEntity, UUID> {
	List<TableEntity> findByOutletId(UUID outletId);
	List<TableEntity> findByAreaId(UUID areaId);

	@Query("select t.status from TableEntity t where t.id = :id")
	String statusOf(@Param("id") UUID id);

	@Modifying(clearAutomatically = false, flushAutomatically = false)
	@Query(value = "update tables set status = :status where id = :id", nativeQuery = true)
	int updateStatus(@Param("id") UUID id, @Param("status") String status);
}
