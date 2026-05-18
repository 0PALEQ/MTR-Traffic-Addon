package com.cookiecraftmods.mta.client.render.custom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class JsonTrafficModelLoader {
	private JsonTrafficModelLoader() {
	}

	static List<TrafficMeshFace> load(JsonObject root) {
		final List<TrafficMeshFace> faces = new ArrayList<>();
		final JsonArray elements = root.has("elements") && root.get("elements").isJsonArray()
			? root.getAsJsonArray("elements")
			: root.has("cubes") && root.get("cubes").isJsonArray() ? root.getAsJsonArray("cubes") : null;
		if (elements == null) {
			return faces;
		}

		for (JsonElement element : elements) {
			if (element.isJsonObject()) {
				addCuboid(element.getAsJsonObject(), faces);
			}
		}
		return faces;
	}

	private static void addCuboid(JsonObject object, List<TrafficMeshFace> faces) {
		final Bounds bounds = readBounds(object);
		addQuad(faces, bounds.x1, bounds.y1, bounds.z2, bounds.x2, bounds.y1, bounds.z2, bounds.x2, bounds.y2, bounds.z2, bounds.x1, bounds.y2, bounds.z2, 0, 0, 1);
		addQuad(faces, bounds.x2, bounds.y1, bounds.z1, bounds.x1, bounds.y1, bounds.z1, bounds.x1, bounds.y2, bounds.z1, bounds.x2, bounds.y2, bounds.z1, 0, 0, -1);
		addQuad(faces, bounds.x1, bounds.y2, bounds.z2, bounds.x2, bounds.y2, bounds.z2, bounds.x2, bounds.y2, bounds.z1, bounds.x1, bounds.y2, bounds.z1, 0, 1, 0);
		addQuad(faces, bounds.x1, bounds.y1, bounds.z1, bounds.x2, bounds.y1, bounds.z1, bounds.x2, bounds.y1, bounds.z2, bounds.x1, bounds.y1, bounds.z2, 0, -1, 0);
		addQuad(faces, bounds.x2, bounds.y1, bounds.z2, bounds.x2, bounds.y1, bounds.z1, bounds.x2, bounds.y2, bounds.z1, bounds.x2, bounds.y2, bounds.z2, 1, 0, 0);
		addQuad(faces, bounds.x1, bounds.y1, bounds.z1, bounds.x1, bounds.y1, bounds.z2, bounds.x1, bounds.y2, bounds.z2, bounds.x1, bounds.y2, bounds.z1, -1, 0, 0);
	}

	private static Bounds readBounds(JsonObject object) {
		if (object.has("from") && object.has("to")) {
			final JsonArray from = object.getAsJsonArray("from");
			final JsonArray to = object.getAsJsonArray("to");
			return new Bounds(value(from, 0) / 16.0F - 0.5F, value(from, 1) / 16.0F, value(from, 2) / 16.0F - 0.5F, value(to, 0) / 16.0F - 0.5F, value(to, 1) / 16.0F, value(to, 2) / 16.0F - 0.5F);
		}

		final JsonArray origin = object.has("origin") ? object.getAsJsonArray("origin") : null;
		final JsonArray size = object.has("size") ? object.getAsJsonArray("size") : null;
		final float x = origin == null ? 0.0F : value(origin, 0) / 16.0F;
		final float y = origin == null ? 0.0F : value(origin, 1) / 16.0F;
		final float z = origin == null ? 0.0F : value(origin, 2) / 16.0F;
		final float sx = size == null ? 1.0F : value(size, 0) / 16.0F;
		final float sy = size == null ? 1.0F : value(size, 1) / 16.0F;
		final float sz = size == null ? 1.0F : value(size, 2) / 16.0F;
		return new Bounds(x - 0.5F, y, z - 0.5F, x + sx - 0.5F, y + sy, z + sz - 0.5F);
	}

	private static void addQuad(List<TrafficMeshFace> faces, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float nx, float ny, float nz) {
		final TrafficMeshVertex first = new TrafficMeshVertex(x1, y1, z1, 0.0F, 1.0F);
		final TrafficMeshVertex second = new TrafficMeshVertex(x2, y2, z2, 1.0F, 1.0F);
		final TrafficMeshVertex third = new TrafficMeshVertex(x3, y3, z3, 1.0F, 0.0F);
		final TrafficMeshVertex fourth = new TrafficMeshVertex(x4, y4, z4, 0.0F, 0.0F);
		faces.add(new TrafficMeshFace(List.of(first, second, third), nx, ny, nz));
		faces.add(new TrafficMeshFace(List.of(first, third, fourth), nx, ny, nz));
	}

	private static float value(JsonArray array, int index) {
		return index < array.size() && array.get(index).isJsonPrimitive() ? array.get(index).getAsFloat() : 0.0F;
	}

	private record Bounds(float x1, float y1, float z1, float x2, float y2, float z2) {
	}
}
