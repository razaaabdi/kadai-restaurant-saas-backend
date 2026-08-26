package com.restaurant.inventory.application;

import com.restaurant.inventory.domain.StockTransactionType;
import com.restaurant.inventory.infrastructure.InventoryItemEntity;
import com.restaurant.inventory.infrastructure.InventoryItemRepository;
import com.restaurant.inventory.infrastructure.StockBalanceEntity;
import com.restaurant.inventory.infrastructure.StockBalanceRepository;
import com.restaurant.inventory.infrastructure.StockLocationEntity;
import com.restaurant.inventory.infrastructure.StockLocationRepository;
import com.restaurant.inventory.infrastructure.StockTransactionEntity;
import com.restaurant.inventory.infrastructure.StockTransactionRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.Money;
import com.restaurant.platform.api.Quantity;
import com.restaurant.platform.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class StockLedgerService {
	private final StockTransactionRepository txs;
	private final StockBalanceRepository balances;
	private final StockLocationRepository locations;
	private final InventoryItemRepository items;

	public StockLedgerService(StockTransactionRepository txs, StockBalanceRepository balances,
			StockLocationRepository locations, InventoryItemRepository items) {
		this.txs = txs;
		this.balances = balances;
		this.locations = locations;
		this.items = items;
	}

	@Transactional
	public StockTransactionEntity post(UUID outletId, UUID inventoryItemId, UUID locationId, StockTransactionType type,
			BigDecimal signedQty, long unitCostPaise, UUID referenceId, String referenceType, String reason, String notes,
			boolean allowNegative) {
		if (signedQty == null || signedQty.compareTo(BigDecimal.ZERO) == 0) {
			throw ApiException.bad("ITEM_QTY", "Quantity must not be zero");
		}
		InventoryItemEntity item = items.findById(inventoryItemId)
				.orElseThrow(() -> ApiException.notFound("ITEM", "Inventory item not found"));
		if (!item.getOutletId().equals(outletId)) throw ApiException.notFound("ITEM", "Inventory item not found");
		UUID loc = locationId == null ? defaultLocation(outletId).getId()
				: locations.findByIdAndOutletId(locationId, outletId)
						.orElseThrow(() -> ApiException.notFound("STOCK_LOCATION", "Stock location not found")).getId();
		Quantity qty = new Quantity(signedQty);
		long totalCost = new Money(unitCostPaise).times(qty.value().abs()).paise();
		var p = TenantContext.require();

		StockTransactionEntity tx = new StockTransactionEntity();
		tx.setTenantId(p.tenantId());
		tx.setOutletId(outletId);
		tx.setStockLocationId(loc);
		tx.setInventoryItemId(inventoryItemId);
		tx.setType(type.name());
		tx.setQty(qty.value());
		tx.setUnit(item.getUnit());
		tx.setUnitCostPaise(unitCostPaise);
		tx.setTotalCostPaise(totalCost);
		tx.setReferenceId(referenceId);
		tx.setReferenceType(referenceType);
		tx.setReason(reason);
		tx.setNotes(notes);
		tx.setPerformedBy(p.userId());
		tx.setBusinessDate(LocalDate.now(ZoneOffset.UTC));
		if (referenceId != null && "ORDER".equals(referenceType)) tx.setOrderId(referenceId);
		txs.save(tx);

		StockBalanceEntity b = balances.lockByOutletLocationItem(outletId, loc, inventoryItemId).orElseGet(() -> {
			StockBalanceEntity n = new StockBalanceEntity();
			n.setTenantId(p.tenantId());
			n.setOutletId(outletId);
			n.setStockLocationId(loc);
			n.setInventoryItemId(inventoryItemId);
			n.setQty(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
			return balances.saveAndFlush(n);
		});
		BigDecimal next = new Quantity(b.getQty().add(qty.value())).value();
		if (!allowNegative && next.compareTo(BigDecimal.ZERO) < 0) {
			throw ApiException.conflict("INSUFFICIENT_STOCK", "Available stock is below required quantity.");
		}
		long avg = b.getAverageCostPaise();
		if ((type == StockTransactionType.OPENING_STOCK || type == StockTransactionType.PURCHASE
				|| type == StockTransactionType.ADJUSTMENT_IN)
				&& qty.value().compareTo(BigDecimal.ZERO) > 0 && unitCostPaise > 0) {
			BigDecimal oldQty = b.getQty().max(BigDecimal.ZERO);
			BigDecimal newQty = oldQty.add(qty.value());
			if (newQty.compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal weighted = oldQty.multiply(BigDecimal.valueOf(avg))
						.add(qty.value().multiply(BigDecimal.valueOf(unitCostPaise)));
				avg = weighted.divide(newQty, 0, RoundingMode.HALF_UP).longValue();
			} else {
				avg = unitCostPaise;
			}
		}
		b.setQty(next);
		b.setAverageCostPaise(avg);
		BigDecimal valuedQty = next.max(BigDecimal.ZERO);
		b.setInventoryValuePaise(new Money(avg).times(valuedQty).paise());
		b.setUpdatedAt(Instant.now());
		balances.save(b);
		return tx;
	}

	public StockLocationEntity defaultLocation(UUID outletId) {
		return locations.findFirstByOutletIdAndNameIgnoreCase(outletId, "Main Store").orElseGet(() -> {
			StockLocationEntity loc = new StockLocationEntity();
			loc.setTenantId(TenantContext.require().tenantId());
			loc.setOutletId(outletId);
			loc.setName("Main Store");
			loc.setType("MAIN_STORE");
			return locations.save(loc);
		});
	}
}
