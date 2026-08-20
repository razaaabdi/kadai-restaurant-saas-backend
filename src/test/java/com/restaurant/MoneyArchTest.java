package com.restaurant;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class MoneyArchTest {
	@Test
	void noPrimitiveFloatOrDoubleInDomainMoneyTypes() {
		var classes = new ClassFileImporter().importPackages("com.restaurant.platform.api", "com.restaurant.order.domain",
				"com.restaurant.billing", "com.restaurant.payment", "com.restaurant.inventory");
		ArchRuleDefinition.noFields().that().areDeclaredInClassesThat().resideInAnyPackage(
						"com.restaurant.platform.api", "com.restaurant.order.domain", "com.restaurant.billing..",
						"com.restaurant.payment..", "com.restaurant.inventory..")
				.should().haveRawType(double.class)
				.orShould().haveRawType(float.class)
				.orShould().haveRawType(Double.class)
				.orShould().haveRawType(Float.class)
				.check(classes);
	}
}
