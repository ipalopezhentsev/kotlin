package ru.iliks.coroutines

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
class TestCoroutines {
    companion object {
        val log = LoggerFactory.getLogger(TestCoroutines::class.java)
    }

    @Test
    fun test() = runBlocking {
        GlobalScope.launch {
            delay(2000)
            log.info("World")
        }
        log.info("Hello ")
        delay(2000)
    }

    private suspend fun getA(): String {
        delay(1000L)
        return "A"
    }

    private suspend fun getB(): String {
        delay(1000L)
        return "B"
    }

    @Test
    fun `test sequential`() = runBlocking {
        //note measureTime is not suspend fun
        val time = measureTime {
            val a: String = getA()
            //note it's String, not Future<String>
            val b: String = getB()
            log.info("a=$a, b=$b")
        }
        //will be approx 2 secs because by default coroutines start one after another
        log.info("Duration={}", time)
    }

    @Test
    fun `test parallel`() = runBlocking {
        val time = measureTime {
            val a: Deferred<String> = async { getA() }
            val b: Deferred<String> = async { getB() }
            awaitAll(a, b)
            log.info("a={}, b={}", a, b)
        }
        //will be approx 1 sec because we changed default
        //sequential style with async {} (analog of await from C#)
        //and now a & b are computed in parallel
        log.info("Duration={}", time)
    }
}