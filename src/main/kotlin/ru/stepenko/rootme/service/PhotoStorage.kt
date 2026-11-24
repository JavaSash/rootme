package ru.stepenko.rootme.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import ru.stepenko.rootme.exception.ApiFailure
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

internal data class StoredPhoto(
    val format: PhotoFormat,
    val dimensions: ImageDimensions,
    val size: Long,
    val sha256: String,
)

@Component
class PhotoStorage(
    @Value("\${rootme.storage.photos-directory:data/photos}") directory: String,
    private val images: PhotoImages,
) {
    private val location = Path.of(directory).toAbsolutePath().normalize()
    private val monitor = Any()
    private val logger = LoggerFactory.getLogger(javaClass)
    private val maxBytes = 20L * 1024 * 1024
    private val stagingPrefix = ".upload-"
    private val pending = ".pending"

    // This lock protects publication and recovery even when two local processes share the directory.
    internal fun <T> exclusive(create: Boolean, work: (Path) -> T): T? = synchronized(monitor) {
        if (!create && Files.notExists(location)) return@synchronized null
        if (create) Files.createDirectories(location)
        val root = location.toRealPath()
        FileChannel.open(root.resolve(".rootme-photo.lock"), CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            channel.lock().use { work(root) }
        }
    }

    internal fun publish(root: Path, id: UUID, file: MultipartFile): StoredPhoto {
        if (file.size > maxBytes) tooLarge()
        val staging = root.resolve("$stagingPrefix$id")
        Files.createDirectory(staging)
        try {
            val raw = staging.resolve("upload.bin")
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            file.inputStream.use { input ->
                DigestOutputStream(Files.newOutputStream(raw), digest).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        count += read
                        if (count > maxBytes) tooLarge()
                        output.write(buffer, 0, read)
                    }
                }
            }
            val format = images.detect(raw)
            val original = Files.move(raw, staging.resolve("original.${format.extension}"))
            val thumbnail = staging.resolve("thumbnail.${format.extension}")
            val dimensions = images.thumbnail(original, thumbnail, format)
            val marker = Files.createFile(staging.resolve(pending))
            listOf(original, thumbnail, marker).forEach { path ->
                FileChannel.open(path, WRITE).use { it.force(true) }
            }
            Files.move(staging, root.resolve(id.toString()), ATOMIC_MOVE)
            return StoredPhoto(format, dimensions, count, HexFormat.of().formatHex(digest.digest()))
        } finally {
            retryable(id) { deleteDirectory(staging) }
        }
    }

    internal fun resource(id: UUID, key: String, format: PhotoFormat, thumbnail: Boolean): FileSystemResource {
        if (key != this.key(id, format, thumbnail)) throw IOException("Unexpected photo key")
        if (Files.notExists(location)) missingFile()
        val root = location.toRealPath()
        val directory = root.resolve(id.toString())
        if (Files.isSymbolicLink(directory)) throw IOException("Unexpected symbolic link")
        val path = root.resolve(key)
        if (Files.isSymbolicLink(path)) throw IOException("Unexpected symbolic link")
        if (Files.notExists(path)) missingFile()
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || !Files.isReadable(path)) throw IOException("Photo unreadable")
        return FileSystemResource(path)
    }

    internal fun finish(root: Path, id: UUID) {
        val directory = root.resolve(id.toString())
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) throw IOException("Unexpected photo directory")
        Files.deleteIfExists(directory.resolve(pending))
    }

    internal fun remove(root: Path, id: UUID) = deleteDirectory(root.resolve(id.toString()))

    internal fun recoverUploads(root: Path, exists: (UUID) -> Boolean) {
        Files.newDirectoryStream(root).use { entries ->
            for (entry in entries) {
                val name = entry.fileName.toString()
                val id = canonicalId(name.removePrefix(stagingPrefix)) ?: continue
                if (name.startsWith(stagingPrefix)) {
                    retryable(id) { deleteDirectory(entry) }
                } else if (Files.isDirectory(entry, NOFOLLOW_LINKS) && Files.exists(entry.resolve(pending), NOFOLLOW_LINKS)) {
                    retryable(id) { if (exists(id)) finish(root, id) else remove(root, id) }
                }
            }
        }
    }

    internal fun retryable(id: UUID, action: () -> Unit) {
        try {
            action()
        } catch (error: IOException) {
            logger.warn("Photo file cleanup pending for {} ({})", id, error.javaClass.simpleName)
        }
    }

    private fun deleteDirectory(directory: Path) {
        if (Files.notExists(directory, NOFOLLOW_LINKS)) return
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) throw IOException("Unexpected photo directory")
        for (name in listOf("upload.bin", "original.jpg", "original.png", "thumbnail.jpg", "thumbnail.png", pending)) {
            val file = directory.resolve(name)
            if (Files.isDirectory(file, NOFOLLOW_LINKS)) throw IOException("Unexpected directory in photo storage")
            Files.deleteIfExists(file)
        }
        Files.delete(directory)
    }

    private fun canonicalId(name: String) = runCatching { UUID.fromString(name) }.getOrNull()?.takeIf { it.toString() == name }

    internal fun key(id: UUID, format: PhotoFormat, thumbnail: Boolean): String =
        "$id/${if (thumbnail) "thumbnail" else "original"}.${format.extension}"

    private fun tooLarge(): Nothing =
        throw ApiFailure(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "Use an image of at most 20 MiB.")

    private fun missingFile(): Nothing =
        throw ApiFailure(HttpStatus.NOT_FOUND, "PHOTO_FILE_MISSING", "The stored photograph file is missing.")
}
