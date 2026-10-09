package com.cookiecraftmods.mta;

import org.junit.jupiter.api.Test;
import org.mtr.mod.data.RailType;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Java17RuntimeTest {
	@Test
	void loadsAddonAndTransformedMtrRailTypeOnJava17() throws ClassNotFoundException {
		assertEquals(17, Runtime.version().feature(), "Run compatibility tests on the minimum supported Java runtime");
		assertEquals(MTRTrafficAddon.class, Class.forName("com.cookiecraftmods.mta.MTRTrafficAddon"));
		assertTrue(RailType.values().length > 0);
		assertTrue(Arrays.stream(RailType.class.getDeclaredMethods())
			.anyMatch(method -> method.getName().contains("mta$overrideTrafficConnectorRailColor")),
			"Load RailType through Fabric with the addon's mixin applied");
	}
}
