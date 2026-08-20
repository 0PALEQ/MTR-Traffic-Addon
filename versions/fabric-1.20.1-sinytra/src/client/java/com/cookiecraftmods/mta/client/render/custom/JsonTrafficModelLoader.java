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
		final ModelContext context = ModelContext.from(root);
		final JsonArray elements = root.has("elements") && root.get("elements").isJsonArray()
			? root.getAsJsonArray("elements")
			: root.has("cubes") && root.get("cubes").isJsonArray() ? root.getAsJsonArray("cubes") : null;
		if (elements == null) {
			return faces;
		}

		for (JsonElement element : elements) {
			if (element.isJsonObject()) {
				addCuboid(element.getAsJsonObject(), faces, context);
			}
		}
		return faces;
	}

	private static void addCuboid(JsonObject object, List<TrafficMeshFace> faces, ModelContext context) {
		if (object.has("export") && object.get("export").isJsonPrimitive() && !object.get("export").getAsBoolean()) {
			return;
		}

		final Bounds bounds = readBounds(object, context);
		addQuad(faces, object, "south", context, bounds.x1, bounds.y1, bounds.z2, bounds.x2, bounds.y1, bounds.z2, bounds.x2, bounds.y2, bounds.z2, bounds.x1, bounds.y2, bounds.z2);
		addQuad(faces, object, "north", context, bounds.x2, bounds.y1, bounds.z1, bounds.x1, bounds.y1, bounds.z1, bounds.x1, bounds.y2, bounds.z1, bounds.x2, bounds.y2, bounds.z1);
		addQuad(faces, object, "up", context, bounds.x1, bounds.y2, bounds.z2, bounds.x2, bounds.y2, bounds.z2, bounds.x2, bounds.y2, bounds.z1, bounds.x1, bounds.y2, bounds.z1);
		addQuad(faces, object, "down", context, bounds.x1, bounds.y1, bounds.z1, bounds.x2, bounds.y1, bounds.z1, bounds.x2, bounds.y1, bounds.z2, bounds.x1, bounds.y1, bounds.z2);
		addQuad(faces, object, "east", context, bounds.x2, bounds.y1, bounds.z2, bounds.x2, bounds.y1, bounds.z1, bounds.x2, bounds.y2, bounds.z1, bounds.x2, bounds.y2, bounds.z2);
		addQuad(faces, object, "west", context, bounds.x1, bounds.y1, bounds.z1, bounds.x1, bounds.y1, bounds.z2, bounds.x1, bounds.y2, bounds.z2, bounds.x1, bounds.y2, bounds.z1);
	}

	private static Bounds readBounds(JsonObject object, ModelContext context) {
		if (object.has("from") && object.has("to")) {
			final JsonArray from = object.getAsJsonArray("from");
			final JsonArray to = object.getAsJsonArray("to");
			return new Bounds(context.positionX(value(from, 0)), context.positionY(value(from, 1)), context.positionZ(value(from, 2)), context.positionX(value(to, 0)), context.positionY(value(to, 1)), context.positionZ(value(to, 2)));
		}

		final JsonArray origin = object.has("origin") ? object.getAsJsonArray("origin") : null;
		final JsonArray size = object.has("size") ? object.getAsJsonArray("size") : null;
		final float x = origin == null ? context.positionX(0.0F) : context.positionX(value(origin, 0));
		final float y = origin == null ? context.positionY(0.0F) : context.positionY(value(origin, 1));
		final float z = origin == null ? context.positionZ(0.0F) : context.positionZ(value(origin, 2));
		final float sx = size == null ? 1.0F : value(size, 0) / 16.0F;
		final float sy = size == null ? 1.0F : value(size, 1) / 16.0F;
		final float sz = size == null ? 1.0F : value(size, 2) / 16.0F;
		return new Bounds(x, y, z, x + sx, y + sy, z + sz);
	}

	private static void addQuad(List<TrafficMeshFace> faces, JsonObject object, String faceName, ModelContext context, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
		final FaceUv uv = readFaceUv(object, faceName, context);
		final Vec3 firstPosition = rotate(new Vec3(x1, y1, z1), object, context);
		final Vec3 secondPosition = rotate(new Vec3(x2, y2, z2), object, context);
		final Vec3 thirdPosition = rotate(new Vec3(x3, y3, z3), object, context);
		final Vec3 fourthPosition = rotate(new Vec3(x4, y4, z4), object, context);
		final Vec3 normal = calculateNormal(firstPosition, secondPosition, thirdPosition);
		final TrafficMeshVertex first = new TrafficMeshVertex(firstPosition.x(), firstPosition.y(), firstPosition.z(), uv.u1(), uv.v2());
		final TrafficMeshVertex second = new TrafficMeshVertex(secondPosition.x(), secondPosition.y(), secondPosition.z(), uv.u2(), uv.v2());
		final TrafficMeshVertex third = new TrafficMeshVertex(thirdPosition.x(), thirdPosition.y(), thirdPosition.z(), uv.u2(), uv.v1());
		final TrafficMeshVertex fourth = new TrafficMeshVertex(fourthPosition.x(), fourthPosition.y(), fourthPosition.z(), uv.u1(), uv.v1());
		faces.add(new TrafficMeshFace(List.of(first, second, third), normal.x(), normal.y(), normal.z()));
		faces.add(new TrafficMeshFace(List.of(first, third, fourth), normal.x(), normal.y(), normal.z()));
	}

	private static FaceUv readFaceUv(JsonObject object, String faceName, ModelContext context) {
		if (object.has("faces") && object.get("faces").isJsonObject()) {
			final JsonObject faces = object.getAsJsonObject("faces");
			if (faces.has(faceName) && faces.get(faceName).isJsonObject()) {
				final JsonObject face = faces.getAsJsonObject(faceName);
				if (face.has("uv") && face.get("uv").isJsonArray() && face.getAsJsonArray("uv").size() >= 4) {
					final JsonArray uv = face.getAsJsonArray("uv");
					return new FaceUv(value(uv, 0) / context.textureWidth(), value(uv, 1) / context.textureHeight(), value(uv, 2) / context.textureWidth(), value(uv, 3) / context.textureHeight());
				}
			}
		}
		return FaceUv.DEFAULT;
	}

	private static Vec3 rotate(Vec3 position, JsonObject object, ModelContext context) {
		if (!object.has("rotation") || !object.get("rotation").isJsonArray()) {
			return position;
		}

		final JsonArray rotation = object.getAsJsonArray("rotation");
		final JsonArray originArray = object.has("origin") && object.get("origin").isJsonArray() ? object.getAsJsonArray("origin") : null;
		final Vec3 origin = originArray == null ? Vec3.ZERO : new Vec3(context.positionX(value(originArray, 0)), context.positionY(value(originArray, 1)), context.positionZ(value(originArray, 2)));
		Vec3 rotated = position.subtract(origin);
		rotated = rotateX(rotated, (float) Math.toRadians(value(rotation, 0)));
		rotated = rotateY(rotated, (float) Math.toRadians(value(rotation, 1)));
		rotated = rotateZ(rotated, (float) Math.toRadians(value(rotation, 2)));
		return rotated.add(origin);
	}

	private static Vec3 rotateX(Vec3 position, float radians) {
		if (radians == 0.0F) {
			return position;
		}
		final float cos = (float) Math.cos(radians);
		final float sin = (float) Math.sin(radians);
		return new Vec3(position.x(), position.y() * cos - position.z() * sin, position.y() * sin + position.z() * cos);
	}

	private static Vec3 rotateY(Vec3 position, float radians) {
		if (radians == 0.0F) {
			return position;
		}
		final float cos = (float) Math.cos(radians);
		final float sin = (float) Math.sin(radians);
		return new Vec3(position.x() * cos + position.z() * sin, position.y(), -position.x() * sin + position.z() * cos);
	}

	private static Vec3 rotateZ(Vec3 position, float radians) {
		if (radians == 0.0F) {
			return position;
		}
		final float cos = (float) Math.cos(radians);
		final float sin = (float) Math.sin(radians);
		return new Vec3(position.x() * cos - position.y() * sin, position.x() * sin + position.y() * cos, position.z());
	}

	private static Vec3 calculateNormal(Vec3 first, Vec3 second, Vec3 third) {
		final Vec3 a = second.subtract(first);
		final Vec3 b = third.subtract(first);
		return normalize(new Vec3(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x()));
	}

	private static Vec3 normalize(Vec3 vector) {
		final float length = (float) Math.sqrt(vector.x() * vector.x() + vector.y() * vector.y() + vector.z() * vector.z());
		return length <= 0.0F ? new Vec3(0.0F, 1.0F, 0.0F) : new Vec3(vector.x() / length, vector.y() / length, vector.z() / length);
	}

	private static float value(JsonArray array, int index) {
		return index < array.size() && array.get(index).isJsonPrimitive() ? array.get(index).getAsFloat() : 0.0F;
	}

	private record ModelContext(boolean centeredCoordinates, float textureWidth, float textureHeight) {
		private static ModelContext from(JsonObject root) {
			final boolean blockbenchModel = root.has("meta") || root.has("outliner") || root.has("resolution");
			float width = 16.0F;
			float height = 16.0F;
			if (root.has("resolution") && root.get("resolution").isJsonObject()) {
				final JsonObject resolution = root.getAsJsonObject("resolution");
				width = resolution.has("width") && resolution.get("width").isJsonPrimitive() ? resolution.get("width").getAsFloat() : width;
				height = resolution.has("height") && resolution.get("height").isJsonPrimitive() ? resolution.get("height").getAsFloat() : height;
			} else if (root.has("texture_size") && root.get("texture_size").isJsonArray()) {
				final JsonArray textureSize = root.getAsJsonArray("texture_size");
				width = value(textureSize, 0);
				height = value(textureSize, 1);
			}
			return new ModelContext(blockbenchModel, Math.max(1.0F, width), Math.max(1.0F, height));
		}

		private float positionX(float value) {
			return value / 16.0F - (centeredCoordinates ? 0.0F : 0.5F);
		}

		private float positionY(float value) {
			return value / 16.0F;
		}

		private float positionZ(float value) {
			return value / 16.0F - (centeredCoordinates ? 0.0F : 0.5F);
		}
	}

	private record Bounds(float x1, float y1, float z1, float x2, float y2, float z2) {
	}

	private record FaceUv(float u1, float v1, float u2, float v2) {
		private static final FaceUv DEFAULT = new FaceUv(0.0F, 0.0F, 1.0F, 1.0F);
	}

	private record Vec3(float x, float y, float z) {
		private static final Vec3 ZERO = new Vec3(0.0F, 0.0F, 0.0F);

		private Vec3 add(Vec3 other) {
			return new Vec3(x + other.x, y + other.y, z + other.z);
		}

		private Vec3 subtract(Vec3 other) {
			return new Vec3(x - other.x, y - other.y, z - other.z);
		}
	}
}
