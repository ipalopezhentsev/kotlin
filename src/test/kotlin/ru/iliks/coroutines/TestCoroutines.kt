package ru.iliks.coroutines

import assertk.assertThat
import assertk.assertions.isFalse
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class TestCoroutines {
    companion object {
        val log = LoggerFactory.getLogger(TestCoroutines::class.java)
    }
    @Test
    fun test() {
        log.info("test")
        assertThat(true).isFalse()
    }
}