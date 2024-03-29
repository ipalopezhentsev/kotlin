package ru.iliks.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
class TestCoroutines {
    companion object {
        val log = LoggerFactory.getLogger(TestCoroutines::class.java)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun test() = runTest {
        launch {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test sequential`() = runTest {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test parallel`() = runTest {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test explicit coroutine`() = runTest {
        val exSvc = Executors.newSingleThreadScheduledExecutor()
        try {
            val res = suspendCancellableCoroutine { continuation ->
                exSvc.schedule({ continuation.resume(" World", null) },
                        2, TimeUnit.SECONDS)
            }
            log.info("Hello")
            log.info(res)
        } finally {
            exSvc.shutdown()
        }
    }
}