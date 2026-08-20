package com.restaurant.order.domain;

import com.restaurant.platform.api.ApiException;

import java.util.Map;
import java.util.Set;

public final class OrderStatus {
	public static final String DRAFT = "DRAFT";
	public static final String CONFIRMED = "CONFIRMED";
	public static final String KOT_SENT = "KOT_SENT";
	public static final String PREPARING = "PREPARING";
	public static final String READY = "READY";
	public static final String BILL_REQUESTED = "BILL_REQUESTED";
	public static final String BILLED = "BILLED";
	public static final String PAID = "PAID";
	public static final String COMPLETED = "COMPLETED";
	public static final String CANCELLED = "CANCELLED";
	public static final String VOIDED = "VOIDED";

	private static final Map<String, Set<String>> NEXT = Map.ofEntries(
			Map.entry(DRAFT, Set.of(CONFIRMED, CANCELLED)),
			Map.entry(CONFIRMED, Set.of(KOT_SENT, CANCELLED)),
			Map.entry(KOT_SENT, Set.of(PREPARING, CANCELLED, BILL_REQUESTED)),
			Map.entry(PREPARING, Set.of(READY, CANCELLED, BILL_REQUESTED)),
			Map.entry(READY, Set.of(BILL_REQUESTED)),
			Map.entry(BILL_REQUESTED, Set.of(BILLED)),
			Map.entry(BILLED, Set.of(PAID, VOIDED)),
			Map.entry(PAID, Set.of(COMPLETED)),
			Map.entry(COMPLETED, Set.of()),
			Map.entry(CANCELLED, Set.of()),
			Map.entry(VOIDED, Set.of())
	);

	private OrderStatus() {}

	public static void assertTransition(String from, String to) {
		if (!NEXT.getOrDefault(from, Set.of()).contains(to)) {
			throw ApiException.conflict("ORDER_ILLEGAL_STATUS", "Cannot go from " + from + " to " + to);
		}
	}

	public static boolean open(String status) {
		return !Set.of(COMPLETED, CANCELLED, VOIDED).contains(status);
	}
}
