package com.cookiecraftmods.mta.client.render.custom;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ObjTrafficModelLoader {
	private ObjTrafficModelLoader() {
	}

	static List<TrafficMeshFace> load(Reader reader) throws Exception {
		return load(reader, null, null, null);
	}

	static List<TrafficMeshFace> load(Reader reader, ResourceManager manager, ResourceLocation modelLocation, ResourceLocation defaultTexture) throws Exception {
		final List<Vec3> positions = new ArrayList<>();
		final List<Vec2> textureCoordinates = new ArrayList<>();
		final List<Vec3> normals = new ArrayList<>();
		final List<TrafficMeshFace> faces = new ArrayList<>();
		final Map<String, ResourceLocation> materialTextures = new HashMap<>();
		String currentMaterial = null;

		try (BufferedReader bufferedReader = new BufferedReader(reader)) {
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				final String trimmedLine = line.trim();
				if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
					continue;
				}

				final String[] parts = trimmedLine.split("\\s+");
				switch (parts[0]) {
					case "v" -> positions.add(new Vec3(parseFloat(parts, 1), parseFloat(parts, 2), parseFloat(parts, 3)));
					case "vt" -> textureCoordinates.add(new Vec2(parseFloat(parts, 1), parseFloat(parts, 2)));
					case "vn" -> normals.add(new Vec3(parseFloat(parts, 1), parseFloat(parts, 2), parseFloat(parts, 3)));
					case "mtllib" -> loadMaterialLibraries(parts, manager, modelLocation, defaultTexture, materialTextures);
					case "usemtl" -> currentMaterial = parts.length > 1 ? parts[1] : null;
					case "f" -> addFaces(parts, positions, textureCoordinates, normals, faces, materialTextures.get(currentMaterial));
					default -> {
					}
				}
			}
		}

		return faces;
	}

	private static void addFaces(String[] parts, List<Vec3> positions, List<Vec2> textureCoordinates, List<Vec3> normals, List<TrafficMeshFace> faces, ResourceLocation texture) {
		if (parts.length < 4) {
			return;
		}

		final FaceVertex first = parseFaceVertex(parts[1], positions, textureCoordinates, normals);
		for (int i = 2; i < parts.length - 1; i++) {
			final FaceVertex second = parseFaceVertex(parts[i], positions, textureCoordinates, normals);
			final FaceVertex third = parseFaceVertex(parts[i + 1], positions, textureCoordinates, normals);
			final Vec3 normal = first.normal == null || second.normal == null || third.normal == null
				? calculateNormal(first.position, second.position, third.position)
				: average(first.normal, second.normal, third.normal);
			faces.add(new TrafficMeshFace(List.of(
				toMeshVertex(first),
				toMeshVertex(second),
				toMeshVertex(third)
			), normal.x(), normal.y(), normal.z(), texture));
		}
	}

	private static void loadMaterialLibraries(String[] parts, ResourceManager manager, ResourceLocation modelLocation, ResourceLocation defaultTexture, Map<String, ResourceLocation> materialTextures) throws Exception {
		if (manager == null || modelLocation == null) {
			return;
		}

		for (int i = 1; i < parts.length; i++) {
			final ResourceLocation materialLocation = resolveSibling(modelLocation, parts[i]);
			final Resource resource = manager.getResource(materialLocation).orElse(null);
			if (resource == null) {
				continue;
			}
			readMaterialLibrary(resource, materialLocation, defaultTexture, materialTextures);
		}
	}

	private static void readMaterialLibrary(Resource resource, ResourceLocation materialLocation, ResourceLocation defaultTexture, Map<String, ResourceLocation> materialTextures) throws Exception {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
			String currentMaterial = null;
			String line;
			while ((line = reader.readLine()) != null) {
				final String trimmedLine = line.trim();
				if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
					continue;
				}

				final String[] parts = trimmedLine.split("\\s+");
				switch (parts[0]) {
					case "newmtl" -> currentMaterial = parts.length > 1 ? parts[1] : null;
					case "map_Kd" -> {
						if (currentMaterial != null && parts.length > 1) {
							materialTextures.put(currentMaterial, resolveMaterialTexture(materialLocation, defaultTexture, parts[1]));
						}
					}
					default -> {
					}
				}
			}
		}
	}

	private static ResourceLocation resolveMaterialTexture(ResourceLocation materialLocation, ResourceLocation defaultTexture, String rawPath) {
		if (rawPath.contains(":")) {
			return new ResourceLocation(rawPath);
		}
		if (defaultTexture != null) {
			final String texturePath = defaultTexture.getPath();
			final int slashIndex = texturePath.lastIndexOf('/');
			final String textureDirectory = slashIndex < 0 ? "" : texturePath.substring(0, slashIndex + 1);
			return new ResourceLocation(defaultTexture.getNamespace(), textureDirectory + normalizeRelativePath(rawPath));
		}
		return resolveSibling(materialLocation, rawPath);
	}

	private static ResourceLocation resolveSibling(ResourceLocation sourceLocation, String rawPath) {
		if (rawPath.contains(":")) {
			return new ResourceLocation(rawPath);
		}
		final String sourcePath = sourceLocation.getPath();
		final int slashIndex = sourcePath.lastIndexOf('/');
		final String directory = slashIndex < 0 ? "" : sourcePath.substring(0, slashIndex + 1);
		return new ResourceLocation(sourceLocation.getNamespace(), directory + normalizeRelativePath(rawPath));
	}

	private static String normalizeRelativePath(String rawPath) {
		return rawPath.replace('\\', '/');
	}

	private static FaceVertex parseFaceVertex(String rawValue, List<Vec3> positions, List<Vec2> textureCoordinates, List<Vec3> normals) {
		final String[] indices = rawValue.split("/", -1);
		final Vec3 position = positions.get(resolveIndex(indices[0], positions.size()));
		final Vec2 textureCoordinate = indices.length > 1 && !indices[1].isBlank() ? textureCoordinates.get(resolveIndex(indices[1], textureCoordinates.size())) : new Vec2(0.0F, 0.0F);
		final Vec3 normal = indices.length > 2 && !indices[2].isBlank() ? normals.get(resolveIndex(indices[2], normals.size())) : null;
		return new FaceVertex(position, textureCoordinate, normal);
	}

	private static int resolveIndex(String rawIndex, int size) {
		final int index = Integer.parseInt(rawIndex);
		return index < 0 ? size + index : index - 1;
	}

	private static float parseFloat(String[] parts, int index) {
		return index < parts.length ? Float.parseFloat(parts[index]) : 0.0F;
	}

	private static TrafficMeshVertex toMeshVertex(FaceVertex faceVertex) {
		return new TrafficMeshVertex(faceVertex.position.x(), faceVertex.position.y(), faceVertex.position.z(), faceVertex.textureCoordinate.u(), 1.0F - faceVertex.textureCoordinate.v());
	}

	private static Vec3 calculateNormal(Vec3 first, Vec3 second, Vec3 third) {
		final float ax = second.x() - first.x();
		final float ay = second.y() - first.y();
		final float az = second.z() - first.z();
		final float bx = third.x() - first.x();
		final float by = third.y() - first.y();
		final float bz = third.z() - first.z();
		return normalize(new Vec3(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx));
	}

	private static Vec3 average(Vec3 first, Vec3 second, Vec3 third) {
		return normalize(new Vec3(
			(first.x() + second.x() + third.x()) / 3.0F,
			(first.y() + second.y() + third.y()) / 3.0F,
			(first.z() + second.z() + third.z()) / 3.0F
		));
	}

	private static Vec3 normalize(Vec3 vector) {
		final float length = (float) Math.sqrt(vector.x() * vector.x() + vector.y() * vector.y() + vector.z() * vector.z());
		return length <= 0.0F ? new Vec3(0.0F, 1.0F, 0.0F) : new Vec3(vector.x() / length, vector.y() / length, vector.z() / length);
	}

	private record FaceVertex(Vec3 position, Vec2 textureCoordinate, Vec3 normal) {
	}

	private record Vec2(float u, float v) {
	}

	private record Vec3(float x, float y, float z) {
	}
}
