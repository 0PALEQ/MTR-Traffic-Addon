package com.cookiecraftmods.mta.traffic.lights.block;

import net.minecraft.util.StringRepresentable;

public enum TrafficLightSignalState implements StringRepresentable {
	OFF("off"),
	RED("red"),
	YELLOW("yellow"),
	GREEN("green");

	private final String serializedName;

	TrafficLightSignalState(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	public boolean isLit() {
		return this != OFF;
	}
}
