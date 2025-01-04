package ru.stepenko.rootme.config

import org.neo4j.cypherdsl.core.renderer.Dialect
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Config
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.neo4j.config.AbstractNeo4jConfig
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext

@Configuration
class Neo4jConfig : AbstractNeo4jConfig() {

    @Value("\${spring.neo4j.uri}")
    private lateinit var uri: String

    @Value("\${spring.neo4j.authentication.username}")
    private lateinit var username: String

    @Value("\${spring.neo4j.authentication.password}")
    private lateinit var password: String

    @Bean
    override fun cypherDslConfiguration(): org.neo4j.cypherdsl.core.renderer.Configuration {
        return org.neo4j.cypherdsl.core.renderer.Configuration.newConfig().withDialect(Dialect.NEO4J_5).build()
    }

    override fun driver(): Driver {
        // Конфигурируем драйвер для Neo4j
        val config = Config.builder()
            .withoutEncryption()
            .build()

        // Создаем драйвер для подключения
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password), config)
    }

    @Bean
    fun neo4jMappingContext(): Neo4jMappingContext {
        return Neo4jMappingContext()
    }
}