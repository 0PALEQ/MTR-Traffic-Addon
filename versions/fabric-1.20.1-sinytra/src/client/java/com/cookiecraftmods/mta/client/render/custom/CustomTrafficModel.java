package com.cookiecraftmods.mta.client.render.custom;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record CustomTrafficModel(
	CustomTrafficModelDefinition definition,
	List<TrafficMeshFace> faces
) {
	public CustomTrafficModel {
		faces = List.copyOf(faces);
	}

	public ResourceLocation texture() {
		return definition.texture();
	}
}
