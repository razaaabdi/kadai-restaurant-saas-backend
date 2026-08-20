package com.restaurant.payment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PaymentTxRepository extends JpaRepository<PaymentTxEntity, UUID> {}
