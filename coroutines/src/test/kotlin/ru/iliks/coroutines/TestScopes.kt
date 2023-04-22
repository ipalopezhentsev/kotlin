package ru.iliks.coroutines

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.*
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TestScopes {
    @Test
    fun `test cancel of parent scope cancels coroutineScope`() = runTest {
        val parentJob = launch {
            //coroutineScope does not call cancel on parent scope if its body fails, but rethrows the exception,
            //allowing us to handle it without affecting our job.
            try {
                //as we use coroutineScope, not CoroutineScope(), makes us a child of parent scope
                //and so cancel of a parent will cancel all its children
                coroutineScope {
                    delay(1000)
                }
            } catch (ex: CancellationException) {
                //CancellationException is special, thrown by first suspending method, and does not cancel the parent
                //job like an ordinary exception outside of coroutineScope.
                println("parent job cancelled us")
                throw ex
            }
        }
        delay(500)
        parentJob.cancel()
        parentJob.join()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test coroutineScope waits for completion of its children before returning`() = runTest {
        coroutineScope {
            launch {
                delay(1000)
                println("A")
            }
            //will be run in parallel with "A"
            launch {
                delay(2000)
                println("B")
            }
        }
        //will be run only when all coroutineScope children finish, i.e. after 2 sec
        delay(1000)
        println("C")
        assertThat(currentTime).isEqualTo(3000)
    }
}