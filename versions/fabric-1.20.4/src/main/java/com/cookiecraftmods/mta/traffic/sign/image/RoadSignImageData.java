package com.cookiecraftmods.mta.traffic.sign.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class RoadSignImageData {
	public static final int MAX_BYTES = 512 * 1024;
	public static final int MAX_DIMENSION = 2048;
	public static final long MAX_PIXELS = 4_194_304L;
	public static final int IMAGE_ID_LENGTH = 64;
	private static final byte[] PNG_SIGNATURE = new byte[] {
		(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};

	private RoadSignImageData() {
	}

	public static ValidatedImage validate(byte[] data) throws ImageValidationException {
		if (data == null || data.length == 0) {
			throw new ImageValidationException(ValidationError.INVALID_FORMAT);
		}
		if (data.length > MAX_BYTES) {
			throw new ImageValidationException(ValidationError.TOO_LARGE);
		}
		if (!hasPngHeader(data)) {
			throw new ImageValidationException(ValidationError.INVALID_FORMAT);
		}
		final int headerWidth = readHeaderDimension(data, 16);
		final int headerHeight = readHeaderDimension(data, 20);
		if (data.length < 33
			|| readInt(data, 8) != 13
			|| data[12] != 'I'
			|| data[13] != 'H'
			|| data[14] != 'D'
			|| data[15] != 'R') {
			throw new ImageValidationException(ValidationError.INVALID_FORMAT);
		}
		if (!dimensionsAllowed(headerWidth, headerHeight)) {
			throw new ImageValidationException(ValidationError.INVALID_DIMENSIONS);
		}

		final BufferedImage image;
		try {
			image = ImageIO.read(new ByteArrayInputStream(data));
		} catch (IOException | RuntimeException exception) {
			throw new ImageValidationException(ValidationError.INVALID_FORMAT, exception);
		}
		if (image == null) {
			throw new ImageValidationException(ValidationError.INVALID_FORMAT);
		}
		final int width = image.getWidth();
		final int height = image.getHeight();
		if (!dimensionsAllowed(width, height) || width != headerWidth || height != headerHeight) {
			throw new ImageValidationException(ValidationError.INVALID_DIMENSIONS);
		}
		return new ValidatedImage(hash(data), width, height, data.length);
	}

	public static String normalizeId(String id) {
		if (id == null) {
			return "";
		}
		final String normalized = id.trim().toLowerCase(Locale.ROOT);
		if (normalized.length() != IMAGE_ID_LENGTH) {
			return "";
		}
		for (int index = 0; index < normalized.length(); index++) {
			final char character = normalized.charAt(index);
			if (!((character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'))) {
				return "";
			}
		}
		return normalized;
	}

	private static boolean hasPngHeader(byte[] data) {
		if (data.length < PNG_SIGNATURE.length) {
			return false;
		}
		for (int index = 0; index < PNG_SIGNATURE.length; index++) {
			if (data[index] != PNG_SIGNATURE[index]) {
				return false;
			}
		}
		return true;
	}

	private static int readHeaderDimension(byte[] data, int offset) {
		return data.length >= offset + Integer.BYTES ? readInt(data, offset) : -1;
	}

	private static int readInt(byte[] data, int offset) {
		return (data[offset] & 0xFF) << 24
			| (data[offset + 1] & 0xFF) << 16
			| (data[offset + 2] & 0xFF) << 8
			| data[offset + 3] & 0xFF;
	}

	private static boolean dimensionsAllowed(int width, int height) {
		return width > 0
			&& height > 0
			&& width <= MAX_DIMENSION
			&& height <= MAX_DIMENSION
			&& (long) width * height <= MAX_PIXELS;
	}

	private static String hash(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record ValidatedImage(String id, int width, int height, int byteCount) {
	}

	public enum ValidationError {
		TOO_LARGE,
		INVALID_FORMAT,
		INVALID_DIMENSIONS
	}

	public static final class ImageValidationException extends Exception {
		private final ValidationError error;

		public ImageValidationException(ValidationError error) {
			super(error.name());
			this.error = error;
		}

		public ImageValidationException(ValidationError error, Throwable cause) {
			super(error.name(), cause);
			this.error = error;
		}

		public ValidationError error() {
			return error;
		}
	}
}
