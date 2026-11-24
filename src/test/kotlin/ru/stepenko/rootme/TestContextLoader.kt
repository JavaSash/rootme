package ru.stepenko.rootme

import org.springframework.boot.test.context.SpringBootContextLoader
import org.springframework.test.context.MergedContextConfiguration
import org.testcontainers.containers.PostgreSQLContainer
import java.nio.file.Path
import java.util.UUID

class TestContextLoader : SpringBootContextLoader() {

    companion object {
        private val postgres = PostgreSQLContainer<Nothing>("postgres:16")
        private val photoDirectory = Path.of(System.getProperty("java.io.tmpdir"), "rootme-test-photos-${UUID.randomUUID()}")

        init {
            postgres.start()
        }
    }

    override fun getInlinedProperties(mergedConfig: MergedContextConfiguration?): Array<String> =
        super.getInlinedProperties(mergedConfig) + arrayOf(
            "spring.main.allow-bean-definition-overriding=true",
            "spring.datasource.url=${postgres.jdbcUrl}",
            "spring.datasource.username=${postgres.username}",
            "spring.datasource.password=${postgres.password}",
            "rootme.storage.photos-directory=${photoDirectory.toString().replace('\\', '/')}",
            "rootme.storage.cleanup-initial-delay-ms=3600000",
        )
}
