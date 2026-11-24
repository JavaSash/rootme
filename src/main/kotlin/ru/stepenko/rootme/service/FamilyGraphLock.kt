package ru.stepenko.rootme.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class FamilyGraphLock(private val jdbc: JdbcTemplate) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun lock() {
        // Serialize graph writes until the enclosing transaction finishes.
        jdbc.queryForObject("select 1 from pg_advisory_xact_lock(721460351)", Int::class.java)
    }
}
