package com.cookiecraftmods.mta.client.compat;

import java.util.Locale;

public enum TextCase {
	DEFAULT,
	UPPER,
	LOWER;

	String convert(String text) {
		return switch (this) {
			case UPPER -> text.toUpperCase(Locale.ROOT);
			case LOWER -> text.toLowerCase(Locale.ROOT);
			default -> text;
		};
	}
}
