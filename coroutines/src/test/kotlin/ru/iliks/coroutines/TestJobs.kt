package ru.iliks.coroutines

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.math.sin

class TestJobs {
    @Test
    fun `jobs can be cancelled`() = runBlocking {
        val resByCoroutine = coroutineScope {
            val parentJob = coroutineContext.job
            val childJob42 = async(CoroutineName("childJob42")) {
                val subChildJob35 = async(CoroutineName("subChildJob35")) {
                    delay(2000)
                    35
                }
                assertThat(coroutineContext.job.children.first()).isEqualTo(subChildJob35)
                subChildJob35.invokeOnCompletion { e ->
                    println("subChild completed with $e")
                }
                42 + subChildJob35.await()
            }
            assertThat(parentJob.children.first()).isEqualTo(childJob42)
            childJob42.invokeOnCompletion { e ->
                println("childJob completed with $e")
            }
            delay(100)
            val outerSubchildJob = childJob42.children.first()
            println(outerSubchildJob)
            println(childJob42)

            //will also cancel subchild
            childJob42.cancel("we cancelled you")
            //cancel is not instantaneous, it will cancel on first suspension point.
            //so if we need certainty, we need to join.
            //NOTE: join() does NOT have return type!
            childJob42.join()
            //alternatively:
            childJob42.cancelAndJoin()
            //if we await on cancelled coroutine, we'll get its exception rethrown in our context
            //childJob42.await()
            assertThat(outerSubchildJob.isCancelled)

            3
        }
        //cancel of child coroutines by this coroutine doesn't lead to itself being cancelled too
        //(as long as we don't await() on them...)
        assertThat(resByCoroutine).isEqualTo(3)
    }

    @Test
    fun `job cancel acts only on suspension points`() = runBlocking {
        //so if we are purely CPU bound, we should check job status from time to time
        val job = launch {
            var accum = 0.0
            //takes about 6s to complete
            for (i in 0..200_000_000) {
                accum += sin(i.toDouble())
            }
            //will be printed
            println(accum)
        }
        delay(100)
        //won't cancel until the complete cycle runs
        job.cancelAndJoin()
    }

    @Test
    fun `cancel for CPU bound jobs`() = runBlocking {
        val job = launch {
            var accum = 0.0
            //takes about 6s to complete
            for (i in 0..200_000_000) {
                if (i % 100_000 == 0) {
                    //may reassign us on a different thread... so books don't recommend it, favoring ensureActive().
                    //but it doesn't work in my test while yield does!
                    yield()
                    //is in all books and articles but doesn't work in practice!
                    //ensureActive()
                }
                accum += sin(i.toDouble())
            }
            //will be printed
            println(accum)
        }
        delay(100)
        //will cancel promptly because we check
        job.cancel()
    }
}