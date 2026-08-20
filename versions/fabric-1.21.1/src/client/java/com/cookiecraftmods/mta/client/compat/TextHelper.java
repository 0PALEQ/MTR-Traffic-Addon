package com.cookiecraftmods.mta.client.compat;

import net.minecraft.network.chat.MutableComponent;

public final class TextHelper {
	private TextHelper() {
	}

	public static MutableComponent literal(String text) {
		return net.minecraft.network.chat.Component.literal(text);
	}
}
