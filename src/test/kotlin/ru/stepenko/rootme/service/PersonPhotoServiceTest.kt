package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import ru.stepenko.rootme.BaseIntegrationTest
import ru.stepenko.rootme.exception.ApiFailure
import ru.stepenko.rootme.formPersonRq
import ru.stepenko.rootme.model.api.PhotoFields
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import java.util.zip.CRC32
import javax.imageio.ImageIO

internal class PersonPhotoServiceTest(
    private val persons: PersonService,
    private val photos: PersonPhotoService,
    private val cleanup: PhotoCleanup,
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
    @Value("\${rootme.storage.photos-directory}") photoDirectory: String,
) : BaseIntegrationTest() {
    private val root = Path.of(photoDirectory)

    @Test
    fun `validates actual content compressed size and dimensions before publishing`() {
        val owner = person()
        val hugeDimensions = png(16, 16)
        val header = ByteBuffer.wrap(hugeDimensions)
        header.putInt(16, 40_000_001)
        val crc = CRC32().apply { update(hugeDimensions, 12, 17) }
        header.putInt(29, crc.value.toInt())
        val fixtures = mapOf(
            "UNSUPPORTED_IMAGE_FORMAT" to "<svg/>".toByteArray(),
            "INVALID_IMAGE" to byteArrayOf(-1, -40, -1, 0, 1),
            "PHOTO_TOO_LARGE" to ByteArray(20 * 1024 * 1024 + 1),
            "IMAGE_DIMENSIONS_EXCEEDED" to hugeDimensions,
        )
        for ((code, bytes) in fixtures) {
            assertEquals(code, assertThrows<ApiFailure> { upload(owner, bytes) }.code)
        }
        assertTrue(photos.list(owner).isEmpty())
        assertTrue(photoDirectories().isEmpty())
    }

    @Test
    fun `preserves original bytes creates a scaled thumbnail and edits metadata`() {
        val owner = person()
        val original = png(640, 160)
        val photo = upload(owner, original, PhotoFields("Original caption", "1955-06"))
        assertEquals("image/png", photo.contentType)
        assertEquals("misleading.jpg", photo.originalFileName)
        assertEquals(original.size.toLong(), photo.sizeBytes)
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)), photo.sha256)
        assertArrayEquals(original, photos.image(photo.id, false).second.inputStream.use { it.readBytes() })
        val preview = photos.image(photo.id, true).second.inputStream.use(ImageIO::read)
        assertEquals(320, preview.width)
        assertEquals(80, preview.height)
        val changed = photos.update(photo.id, json.readTree("""{"caption":null}"""))
        assertNull(changed.caption)
        assertEquals("1955-06", changed.takenDate)
    }

    @Test
    fun `does not upscale a small photograph or silently delete missing photo metadata`() {
        val owner = person()
        val photo = upload(owner, png(25, 12))
        val preview = photos.image(photo.id, true).second.inputStream.use(ImageIO::read)
        assertEquals(25, preview.width)
        assertEquals(12, preview.height)
        Files.delete(root.resolve("${photo.id}/original.png"))
        assertEquals("PHOTO_FILE_MISSING", assertThrows<ApiFailure> { photos.image(photo.id, false) }.code)
        assertEquals(photo.id, photos.list(owner).single().id)
    }

    @Test
    fun `applies JPEG EXIF rotation only to the thumbnail`() {
        val original = ByteArrayOutputStream().use { output ->
            assertTrue(ImageIO.write(BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "jpeg", output))
            output.toByteArray()
        }
        val exif = ByteBuffer.allocate(32)
            .put("Exif\u0000\u0000".toByteArray()).putInt(0x4d4d002a).putInt(8)
            .putShort(1).putShort(0x112).putShort(3).putInt(1).putShort(6).putShort(0).putInt(0).array()
        val oriented = original.copyOfRange(0, 2) + byteArrayOf(-1, -31, 0, 34) +
            exif + original.copyOfRange(2, original.size)
        val photo = upload(person(), oriented)

        assertEquals("image/jpeg", photo.contentType)
        assertArrayEquals(oriented, photos.image(photo.id, false).second.inputStream.use { it.readBytes() })
        val preview = photos.image(photo.id, true).second.inputStream.use(ImageIO::read)
        assertEquals(10, preview.width)
        assertEquals(20, preview.height)
    }

    @Test
    fun `primary selection checks ownership and deleting primary promotes the oldest remaining photo`() {
        val owner = person()
        val otherOwner = person()
        val first = upload(owner)
        val second = upload(owner)
        val other = upload(otherOwner)
        assertTrue(first.isPrimary)
        assertFalse(second.isPrimary)
        assertEquals("PHOTO_PERSON_MISMATCH", assertThrows<ApiFailure> {
            photos.selectPrimary(owner, other.id)
        }.code)
        photos.selectPrimary(owner, second.id)
        assertEquals(second.id, persons.details(owner).primaryPhoto?.id)
        photos.delete(second.id)
        assertEquals(first.id, photos.list(owner).single { it.isPrimary }.id)
        cleanup.retry()
        assertFalse(Files.exists(root.resolve(second.id.toString())))
        assertTrue(Files.exists(root.resolve(other.id.toString())))
        photos.delete(first.id)
        assertNull(persons.details(owner).primaryPhoto)
    }

    @Test
    fun `deleting a person queues all their photos and retries a failed file deletion`() {
        val owner = person()
        val first = upload(owner)
        val second = upload(owner)
        val other = upload(person())
        val obstructed = root.resolve("${first.id}/original.png")
        Files.delete(obstructed)
        Files.createDirectory(obstructed)
        try {
            persons.delete(owner)
            assertEquals("PHOTO_NOT_FOUND", assertThrows<ApiFailure> { photos.image(first.id, false) }.code)
            assertEquals(2, queued(first.id, second.id))
            cleanup.retry()
            assertTrue(Files.exists(root.resolve(first.id.toString())))
            assertFalse(Files.exists(root.resolve(second.id.toString())))
            assertEquals(1, queued(first.id, second.id))
        } finally {
            Files.deleteIfExists(obstructed)
        }
        cleanup.retry()
        assertEquals(0, queued(first.id, second.id))
        assertFalse(Files.exists(root.resolve(first.id.toString())))
        assertTrue(Files.exists(root.resolve(other.id.toString())))
    }

    @Test
    fun `failed metadata persistence rolls back the files already published`() {
        val owner = person()
        val before = photoDirectories()
        jdbc.execute("ALTER TABLE person_photos ADD CONSTRAINT reject_rollback_fixture CHECK (caption <> 'rollback-fixture')")
        try {
            assertThrows<DataIntegrityViolationException> { upload(owner, fields = PhotoFields("rollback-fixture")) }
        } finally {
            jdbc.execute("ALTER TABLE person_photos DROP CONSTRAINT reject_rollback_fixture")
        }
        assertTrue(photos.list(owner).isEmpty())
        assertEquals(before, photoDirectories())
    }

    @Test
    fun `recovery keeps published photos with metadata and removes incomplete uploads without metadata`() {
        val committed = upload(person())
        val committedDirectory = root.resolve(committed.id.toString())
        val marker = Files.createFile(committedDirectory.resolve(".pending"))
        val abandoned = Files.createDirectory(root.resolve(UUID.randomUUID().toString()))
        Files.createFile(abandoned.resolve(".pending"))
        Files.createFile(abandoned.resolve("original.png"))
        val staging = Files.createDirectory(root.resolve(".upload-${UUID.randomUUID()}"))
        Files.createFile(staging.resolve("upload.bin"))

        cleanup.retry()

        assertFalse(Files.exists(marker))
        assertTrue(Files.isRegularFile(committedDirectory.resolve("original.png")))
        assertFalse(Files.exists(abandoned))
        assertFalse(Files.exists(staging))
    }

    private fun person() = persons.create(formPersonRq()).id
    private fun upload(owner: UUID, bytes: ByteArray = png(20, 10), fields: PhotoFields = PhotoFields()) =
        photos.upload(owner, MockMultipartFile("file", "../../misleading.jpg", "application/octet-stream", bytes), fields)

    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().use { output ->
        assertTrue(ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output))
        output.toByteArray()
    }

    private fun photoDirectories(): Set<Path> =
        if (Files.notExists(root)) emptySet() else Files.list(root).use { paths ->
            paths.filter { Files.isDirectory(it) }.toList().toSet()
        }

    private fun queued(a: UUID, b: UUID) =
        jdbc.queryForObject("SELECT count(*) FROM photo_file_cleanup WHERE photo_id IN (?, ?)", Int::class.java, a, b)
}
