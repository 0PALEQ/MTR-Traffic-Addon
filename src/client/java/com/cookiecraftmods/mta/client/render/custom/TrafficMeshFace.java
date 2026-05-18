package com.cookiecraftmods.mta.client.render.custom;

import java.util.List;

public record TrafficMeshFace(
	List<TrafficMeshVertex> vertices,
	float normalX,
	float normalY,
	float normalZ
) {
	public TrafficMeshFace {
		vertices = List.copyOf(vertices);
	}
}
