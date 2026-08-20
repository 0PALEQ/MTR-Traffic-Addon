package com.cookiecraftmods.mta.client.render.custom;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record TrafficMeshFace(
	List<TrafficMeshVertex> vertices,
	float normalX,
	float normalY,
	float normalZ,
	ResourceLocation texture
) {
	public TrafficMeshFace(List<TrafficMeshVertex> vertices, float normalX, float normalY, float normalZ) {
		this(vertices, normalX, normalY, normalZ, null);
	}

	public TrafficMeshFace {
		vertices = List.copyOf(vertices);
	}
}
