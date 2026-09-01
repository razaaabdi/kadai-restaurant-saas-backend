package com.restaurant.inventory.api;
import java.math.BigDecimal;
import java.util.UUID;
public record RecipeConsumption(UUID recipeVersionId, BigDecimal portions) {}
