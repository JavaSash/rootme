package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Value
import org.springframework.mock.web.MockMultipartFile
import ru.stepenko.rootme.BaseIntegrationTest
import ru.stepenko.rootme.exception.ApiFailure
import ru.stepenko.rootme.formPersonRq
import ru.stepenko.rootme.model.api.PhotoFields
import ru.stepenko.rootme.model.api.AlternativeNameFields
import ru.stepenko.rootme.model.api.ParentChildRequest
import ru.stepenko.rootme.model.entity.AlternativeNameType.NICKNAME
import ru.stepenko.rootme.model.entity.ParentChildType.ADOPTIVE
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

internal class BackupServiceTest(
    private val people: PersonService,
    private val photos: PersonPhotoService,
    private val snapshot: BackupSnapshot,
    private val storage: PhotoStorage,
    private val restore: BackupRestore,
    private val family: FamilyRelationshipService,
    private val names: AlternativeNameService,
    private val json: ObjectMapper,
    @Value("\${rootme.storage.photos-directory}") private val photoDirectory: String,
) : BaseIntegrationTest() {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `archive preserves profile UUID and original photo and verifies independently`() {
        val person = people.create(formPersonRq().copy(biography = "Family story", birthDate = "1950-05"))
        val original = png()
        val photo = photos.upload(person.id, MockMultipartFile("file", "test.png", "image/png", original), PhotoFields())
        val service = backupService()
        val created = service.create()
        val resource = service.download(created.id)
        assertEquals(created.verification, resource.inputStream.use(service::verify))
        assertEquals(1, created.verification.counts["persons"])
        assertEquals(1, created.verification.counts["person_photos"])
        ZipFile(resource.file).use { zip ->
            assertEquals(setOf("manifest.json", "data.json", "photos/${photo.id}/original.png"), zip.entries().asSequence().map { it.name }.toSet())
            val row = zip.getInputStream(zip.getEntry("data.json")).use { json.readTree(it) }["persons"][0]
            assertEquals(person.id.toString(), row["id"].asText())
            assertEquals("MONTH", row["birth_date_precision"].asText())
            assertEquals("Family story", row["biography"].asText())
            assertArrayEquals(original, zip.getInputStream(zip.getEntry("photos/${photo.id}/original.png")).use { it.readBytes() })
        }
        assertEquals(listOf("${created.id}.zip"), Files.list(directory).use { paths -> paths.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `rejects truncated archives unexpected entries and content with a mismatched digest`() {
        val service = backupService()
        val file = service.download(service.create().id).file
        val truncated = file.readBytes().dropLast(12).toByteArray()
        assertEquals("INVALID_BACKUP", assertThrows<ApiFailure> { service.verify(truncated.inputStream()) }.code)

        for (addUnexpectedEntry in listOf(false, true)) {
            val modified = ByteArrayOutputStream()
            ZipFile(file).use { source ->
                ZipOutputStream(modified).use { target ->
                    for (entry in source.entries().asSequence()) {
                        target.putNextEntry(ZipEntry(entry.name))
                        val bytes = source.getInputStream(entry).use { it.readBytes() }
                        if (entry.name == "data.json" && !addUnexpectedEntry) bytes[0] = '['.code.toByte()
                        target.write(bytes)
                        target.closeEntry()
                    }
                    if (addUnexpectedEntry) {
                        target.putNextEntry(ZipEntry("../escape.txt"))
                        target.write(1)
                        target.closeEntry()
                    }
                }
            }
            assertEquals("INVALID_BACKUP", assertThrows<ApiFailure> { service.verify(modified.toByteArray().inputStream()) }.code)
        }
        assertEquals(1L, Files.list(directory).use { it.count() })
    }

    @Test
    fun `does not publish an incomplete backup when an original photograph is absent`() {
        val person = people.create(formPersonRq())
        val photo = photos.upload(person.id, MockMultipartFile("file", "test.png", "image/png", png()), PhotoFields())
        Files.delete(Path.of(photoDirectory).resolve("${photo.id}/original.png"))
        assertEquals("BACKUP_PHOTO_MISSING", assertThrows<ApiFailure> { backupService().create() }.code)
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    @Test
    fun `restore preserves ids and artwork and refuses to touch an existing tree`() {
        val parent = people.create(formPersonRq().copy(biography = "Biography", birthDate = "1961"))
        val child = people.create(formPersonRq("Child"))
        val alias = names.create(parent.id, AlternativeNameFields("Nickname", NICKNAME))
        val relation = family.addParentChild(ParentChildRequest(parent.id, child.id, ADOPTIVE))
        val original = png()
        val photo = photos.upload(parent.id, MockMultipartFile("file", "test.png", "image/png", original), PhotoFields(takenDate = "1998"))
        val service = backupService()
        val copy = service.create()
        val file = service.download(copy.id)

        assertEquals("BACKUP_TARGET_NOT_EMPTY", assertThrows<ApiFailure> {
            file.inputStream.use { service.restore(it, copy.verification.archiveSha256) }
        }.code)
        assertEquals(parent, people.details(parent.id).person)
        clearUp()

        assertEquals("BACKUP_PREVIEW_CHANGED", assertThrows<ApiFailure> {
            file.inputStream.use { service.restore(it, "0".repeat(64)) }
        }.code)
        assertTrue(people.search("").items.isEmpty())
        assertEquals(copy.verification, file.inputStream.use { service.restore(it, copy.verification.archiveSha256) })
        val details = people.details(parent.id)
        assertEquals(parent, details.person)
        assertEquals(alias, details.alternativeNames.single())
        assertEquals(relation.id, details.parentChildEdges.single().id)
        assertEquals(child.id, details.relatives.single().id)
        assertEquals(photo.id, details.primaryPhoto?.id)
        assertArrayEquals(original, photos.image(photo.id, false).second.inputStream.use { it.readBytes() })
        assertNotNull(photos.image(photo.id, true).second.inputStream.use(ImageIO::read))
    }

    @Test
    fun `photo metadata mismatch rolls back restored rows and published files`() {
        val owner = people.create(formPersonRq())
        val photo = photos.upload(owner.id, MockMultipartFile("file", "test.png", "image/png", png()), PhotoFields())
        val service = backupService()
        val source = service.download(service.create().id).file
        val modifiedArchive = ByteArrayOutputStream()
        ZipFile(source).use { zip ->
            val data = zip.getInputStream(zip.getEntry("data.json")).use { json.readTree(it) }
            (data["person_photos"][0] as ObjectNode).put("width", 13)
            val bytes = json.writeValueAsBytes(data)
            val manifest = zip.getInputStream(zip.getEntry("manifest.json")).use { json.readTree(it) }
            (manifest["entries"]["data.json"] as ObjectNode)
                .put("size", bytes.size)
                .put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
            ZipOutputStream(modifiedArchive).use { target ->
                for (entry in zip.entries().asSequence()) {
                    target.putNextEntry(ZipEntry(entry.name))
                    when (entry.name) {
                        "data.json" -> target.write(bytes)
                        "manifest.json" -> target.write(json.writeValueAsBytes(manifest))
                        else -> zip.getInputStream(entry).use { it.copyTo(target) }
                    }
                    target.closeEntry()
                }
            }
        }
        clearUp()
        val backup = modifiedArchive.toByteArray()
        val preview = service.verify(backup.inputStream())
        assertEquals(1, preview.counts["persons"])
        assertEquals("INVALID_BACKUP", assertThrows<ApiFailure> {
            service.restore(backup.inputStream(), preview.archiveSha256)
        }.code)
        assertTrue(people.search("").items.isEmpty())
        assertFalse(Files.exists(Path.of(photoDirectory).resolve(photo.id.toString())))
    }

    private fun backupService() = BackupService(snapshot, storage, restore, json, directory.toString())

    private fun png() = ByteArrayOutputStream().use {
        check(ImageIO.write(BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "png", it))
        it.toByteArray()
    }
}
