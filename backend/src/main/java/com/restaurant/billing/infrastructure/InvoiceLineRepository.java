package com.restaurant.billing.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface InvoiceLineRepository extends JpaRepository<InvoiceLineEntity, UUID> {}
