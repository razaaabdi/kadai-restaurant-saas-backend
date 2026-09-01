package com.restaurant.billing.infrastructure; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface InvoiceAmendmentRepository extends JpaRepository<InvoiceAmendmentEntity,UUID>{ List<InvoiceAmendmentEntity> findByInvoiceId(UUID invoiceId); }
