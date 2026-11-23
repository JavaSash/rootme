package ru.stepenko.rootme

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.junit.jupiter.SpringExtension
import ru.stepenko.rootme.repository.PersonRepository

@SpringBootTest
@ContextConfiguration(loader = TestContextLoader::class)
@ExtendWith(SpringExtension::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class BaseIntegrationTest {
    @Autowired
    lateinit var personRepository: PersonRepository

    @AfterEach
    fun clearUp() {
        personRepository.deleteAll()
    }
}