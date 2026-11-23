package ru.stepenko.rootme

import org.springframework.boot.test.context.SpringBootContextLoader
import org.springframework.test.context.MergedContextConfiguration
import org.testcontainers.containers.PostgreSQLContainer

class TestContextLoader : SpringBootContextLoader() {

    companion object {
        private val postgres = PostgreSQLContainer<Nothing>("postgres:16")

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
        )
}
