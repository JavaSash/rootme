package ru.stepenko.rootme.service

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import ru.stepenko.rootme.exception.ApiFailure
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.roundToInt

internal enum class PhotoFormat(val extension: String, val contentType: String) {
    JPEG("jpg", "image/jpeg"), PNG("png", "image/png");

    companion object {
        fun fromContentType(contentType: String) = values().single { it.contentType == contentType }
    }
}

internal data class ImageDimensions(val width: Int, val height: Int)

@Component
class PhotoImages {
    internal fun detect(path: Path): PhotoFormat {
        val signature = java.nio.file.Files.newInputStream(path).use { it.readNBytes(8) }
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        return when {
            signature.contentEquals(png) -> PhotoFormat.PNG
            signature.size >= 3 && signature[0] == 0xff.toByte() &&
                signature[1] == 0xd8.toByte() && signature[2] == 0xff.toByte() -> PhotoFormat.JPEG
            else -> throw ApiFailure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_FORMAT", "Use a JPEG or PNG image.")
        }
    }

    internal fun thumbnail(original: Path, thumbnail: Path, format: PhotoFormat): ImageDimensions {
        val (dimensions, decoded) = decode(original, format)
        try {
            val scale = minOf(1.0, 320.0 / maxOf(dimensions.width, dimensions.height))
            val width = (dimensions.width * scale).roundToInt().coerceAtLeast(1)
            val height = (dimensions.height * scale).roundToInt().coerceAtLeast(1)
            val type = if (format == PhotoFormat.PNG) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
            val resized = BufferedImage(width, height, type)
            val graphics = resized.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                graphics.drawImage(decoded, 0, 0, width, height, null)
            } finally {
                graphics.dispose()
            }
            val oriented = orient(resized, orientation(original))
            if (!ImageIO.write(oriented, format.extension, thumbnail.toFile())) throw IOException("Image writer unavailable")
            return dimensions
        } finally {
            decoded.flush()
        }
    }

    private fun decode(path: Path, format: PhotoFormat): Pair<ImageDimensions, BufferedImage> {
        try {
            ImageIO.createImageInputStream(path.toFile()).use { input ->
                val reader = ImageIO.getImageReadersByFormatName(format.extension).next()
                try {
                    reader.setInput(input, true, true)
                    var corrupt = false
                    reader.addIIOReadWarningListener { _, _ -> corrupt = true }
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width <= 0 || height <= 0) invalidImage()
                    if (width.toLong() * height > 40_000_000) {
                        throw ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "IMAGE_DIMENSIONS_EXCEEDED", "Use at most 40 megapixels.")
                    }
                    val image = reader.read(0) ?: invalidImage()
                    if (corrupt) invalidImage()
                    return ImageDimensions(width, height) to image
                } finally {
                    reader.dispose()
                }
            }
        } catch (_: IOException) {
            invalidImage()
        } catch (_: IllegalArgumentException) {
            invalidImage()
        }
    }

    private fun invalidImage(): Nothing =
        throw ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_IMAGE", "The image is incomplete or corrupt.")

    private fun orientation(file: Path) = runCatching {
        ImageMetadataReader.readMetadata(file.toFile()).getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            ?.getInteger(ExifIFD0Directory.TAG_ORIENTATION)
    }.getOrNull() ?: 1

    private fun orient(input: BufferedImage, orientation: Int): BufferedImage {
        if (orientation !in 2..8) return input
        val lastX = input.width - 1
        val lastY = input.height - 1
        val rotated = orientation >= 5
        val result = BufferedImage(
            if (rotated) input.height else input.width, if (rotated) input.width else input.height, input.type,
        )
        for (y in 0..lastY) for (x in 0..lastX) {
            val (outX, outY) = when (orientation) {
                2 -> lastX - x to y
                3 -> lastX - x to lastY - y
                4 -> x to lastY - y
                5 -> y to x
                6 -> lastY - y to x
                7 -> lastY - y to lastX - x
                else -> y to lastX - x
            }
            result.setRGB(outX, outY, input.getRGB(x, y))
        }
        return result
    }
}
