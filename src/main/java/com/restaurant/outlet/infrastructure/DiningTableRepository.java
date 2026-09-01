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

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
		update tables t set status = 'FREE', version = version + 1
		where t.tenant_id = :tenantId and t.outlet_id = :outletId
		  and t.status in ('OCCUPIED', 'BILL_REQUESTED')
		  and not exists (
		    select 1 from orders o where o.table_id = t.id
		      and o.status not in ('COMPLETED', 'CANCELLED', 'VOIDED')
		  )
		""", nativeQuery = true)
	int reconcileOrphanedOccupied(@Param("tenantId") UUID tenantId, @Param("outletId") UUID outletId);

	boolean existsByOutletIdAndCodeIgnoreCaseAndDeletedFalse(UUID outletId, String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from TableEntity t where t.id = :id")
	Optional<TableEntity> findByIdForUpdate(@Param("id") UUID id);
}
