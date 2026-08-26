package com.restaurant.configuration.application;

public record OrderSettings(
		boolean dineInEnabled,
		boolean takeawayEnabled,
		String defaultDineInEntryMode,
		boolean allowAdditionalKot,
		boolean autoCompleteAfterFullPayment,
		String negativeStockPolicy) {
}
