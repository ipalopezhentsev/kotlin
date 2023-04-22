package ru.iliks.coroutines

import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import org.junit.jupiter.api.Test
import java.lang.Exception
import assertk.fail
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger

class TestExceptions {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `exceptions are very unusual, they cancel everything from bottom to top including all levels siblings`() {
        assertThat {
            runTest {
                try {
                    launch {
                        val r1 = async {
                            delay(100)
                            error("qqq")
                        }
                        val r2 = async {
                            delay(200)
                            //because after first 100 millis sibling coroutine r1 will throw and by this call cancel on parent
                            //job, which is also the parent for r2, and parent job will cancel all its children, including r2,
                            //and will also cancel all its parents etc, so all tree of coroutines will be cancelled.
                            //THIS CAN'T BE SOLVED BY TRY-CATCH (EXCEPT WHEN IT DOESN'T LET EXCEPTION GET OUT OF R1
                            //COROUTINE). THE ONLY SOLUTION IS SUPERVISOR SCOPE, SEE NEXT TEST!
                            fail("Not expected to get here!")
                            3
                        }
                        try {
                            r1.await()
                        } catch (ex: Exception) {
                            println(ex)
                        }
                        println("WON'T BE PRINTED! r2=${r2.await()}")
                    }
                } catch (ex: Exception) {
                    //this try catch is to demonstrate that it doesn't have any effect, bubbling of cancels by kotlin
                    //coroutines lib happens not via exceptions but via accessing parent job and cancelling it and so on.
                    fail("not expected")
                }
                //error("qqq") in unrelated subtree will cancel all the way to top, including this!
                //if we change delay to happen before exception, it will get here.
                delay(300)
                fail("not expected to get here!")
            }
        }.isFailure().hasClass(IllegalStateException::class.java)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `UNSUCCESSFUL solving exceptions killing everything via supervisor scope`() = runTest {
        //it's only partly successful because it seems that it affects only first subchildren, inner scopes
        //don't inherit its exceptions ignoring behavior, so on low level cancels happen, they just don't bubble
        //up and kill the parent coroutine.
        supervisorScope {
            val q: Deferred<Int> = async {
                val r1 = async {
                    delay(100)
                    error("qqq")
                }
                val r2 = async {
                    delay(200)
                    fail("Not expected to get here!")
                    3
                }
                try {
                    r1.await()
                } catch (ex: Exception) {
                    println(ex)
                }
                //THIS WON'T REACH HERE, despite outer supervisorScope. this part will be cancelled, the only effect
                //is that r1 throwing error won't cancel parents other unrelated coroutines (see delay(300) below),
                //but it will cancel immediate scope's other children (because immediate scope is not supervisorScope
                //itself)
                val res2 = r2.await()
                println("WILL BE PRINTED! r2=$res2")
                res2
            }
            delay(300)
            println("WILL PRINT THAT q is cancelled! $q")
            assertThat { q.await() }.isFailure().hasMessage("qqq")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `full solving exceptions killing everything via supervisor scope`() = runTest {
        val q: Deferred<Int> = async {
            supervisorScope {
                val r1 = async {
                    delay(100)
                    //because the immediate outer scope is supervisor scope, it will not go cancelling
                    //our siblings/parents like r2 etc when we tell it to cancel by this throw:
                    error("qqq")
                }
                val r2 = async {
                    delay(200)
                    3
                }
                try {
                    r1.await()
                } catch (ex: Exception) {
                    println(ex)
                }
                val res2 = r2.await()
                println("WILL BE PRINTED! r2=$res2")
                res2
            }
        }
        delay(300)
        println("WILL PRINT THAT q is completed. $q")
        assertThat(q.await()).isEqualTo(3)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `how it behaves in simple code`() = runTest {
        val defSum = async {
            val accum = AtomicInteger()
            for (i in 1..10) {
                try {
                    //ok, this function fails. but it doesn't create a scope/job so it is ok to use 'regular'
                    //java style with try catches, it won't cancel parent scope, because we don't let the exception
                    //get out of the only scope.
                    val res = unreliableFunc(i)
                    accum.addAndGet(res)
                } catch (ex: Exception) {
                    //this will fire only one time, for i=5, so the sum will lack precisely one element
                    println("Exception happened for i=$i, but ignoring it and continuing")
                }
            }
            accum.get()
        }
        //note there's no 5!
        assertThat(defSum.await()).isEqualTo(1 + 2 + 3 + 4 + 6 + 7 + 8 + 9 + 10)
    }

    private suspend fun unreliableFunc(i: Int): Int {
        println("starting unreliableFunc for i=$i")
        delay(i.toLong())
        if (i == 5) {
            error("qqq")
        }
        println("finished unreliableFunc for i=$i")
        return i
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `and how simple optimized code turns into unpredictable raising issues like above`() {
        assertThat {
            runTest {
                //let's add nested scopes, so that inner exception passes through them, now we won't have an answer.
                val defSum = async {
                    val asyncs = mutableListOf<Deferred<Int>>()
                    for (i in 1..10) {
                        try {
                            //Seemingly harmless change from previous test. As unreliableFunc() is potentially
                            //slow, let's wrap it into async so that different invocations may go in parallel.
                            //But unreliableFunc() fails for i=5. As it's in a job (started by async), default
                            //'strange' processing of exceptions starts, it will cancel all parent jobs/siblings too.
                            asyncs.add(async {
                                //the fact that this function throws will not simply throw the exception, it will
                                //CALL CANCEL ON PARENT JOB, PARENT JOB WILL CALL CANCELS ON ALL ITS CHILDREN AND ON ITS
                                //PARENT, ITS PARENT WILL CALL CANCEL ON ALL ITS CHILDREN AND SO ON!
                                unreliableFunc(i)
                            })
                        } catch (ex: Exception) {
                            //we WON'T get here
                            println("Exception happened for i=$i, but ignoring it and continuing")
                            fail("not expected")
                        }
                    }
                    var sum = 0
                    asyncs.forEachIndexed { idx, async ->
                        try {
                            val i = async.await()
                            sum += i
                        } catch (ex: Exception) {
                            //NOT ONLY 5TH async will throw, there will be 1 genuine fail (for 5) and like 6
                            //other (but random number!) of cancelled asyncs, the rest will be successful,
                            //but it's because they finished before the 5th one threw the exception
                            println("Exception happened for i=${idx + 1}, but ignoring it and continuing: $ex")
                        }
                    }
                    println("Calculated sum $sum but it doesn't have all summands except 5 and it won't get to parent")
                    sum
                }
                //THERE'S NO ANSWER - it will throw because first deep cancel cancelled all parents as well.
                //ALL THIS DESPITE WE TRIED TO PLAY IT SAFE AND IGNORE FAILED SUB-PIECES.
                //we can solve it only via SupervisorJob as outer job.
                defSum.await()
                fail("won't get here!")
                //assertThat {defSum.await() }.isFailure().hasMessage("qqq")
            }
        }.isFailure().hasMessage("qqq")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `proper fix for async optimization`() {
        runTest {
            //suppose we still want to call unreliableFunc async and get sum of all good invocations and ignoring bad ones.
            val job = SupervisorJob(coroutineContext.job)
            val nonFailingScope = CoroutineScope(coroutineContext + job)
            //it's not enough to make this async on nonFailingScope, we need nested ones too or only them.
            val defSum = async {
                val asyncs = mutableListOf<Deferred<Int>>()
                for (i in 1..10) {
                    val def = nonFailingScope.async {
                        unreliableFunc(i)
                    }
                    asyncs.add(def)
                }
                var sum = 0
                asyncs.forEachIndexed { idx, async ->
                    try {
                        val i = async.await()
                        sum += i
                    } catch (ex: Exception) {
                        println("Exception happened for i=${idx + 1}, but ignoring it and continuing: $ex")
                    }
                }
                println("Calculated sum $sum")
                sum
            }
            assertThat(defSum.await()).isEqualTo(1 + 2 + 3 + 4 + 6 + 7 + 8 + 9 + 10)
            //if we don't call this, it will never exit because it makes itself ready to accept new and new child
            //coroutines and won't exit due to structured concurrency. So we must tell it explicitly there will be
            //no more new children.
            job.complete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a bit shorter proper fix for async optimization`() {
        runTest {
            //suppose we still want to call unreliableFunc async and get sum of all good invocations and ignoring bad ones.
            //it's not enough to make this async on nonFailingScope, we need nested ones too or only them.
            val defSum = async {
                val asyncs = mutableListOf<Deferred<Int>>()
                //remember that supervisorScope BOTH doesn't throw exceptions after its child jobs throw
                //AND doesn't kill parent job
                supervisorScope {
                    for (i in 1..10) {
                        val def = async {
                            unreliableFunc(i)
                        }
                        asyncs.add(def)
                    }
                }

                var sum = 0
                asyncs.forEachIndexed { idx, async ->
                    try {
                        val i = async.await()
                        sum += i
                    } catch (ex: Exception) {
                        println("Exception happened for i=${idx + 1}, but ignoring it and continuing: $ex")
                    }
                }
                println("Calculated sum $sum")
                sum
            }
            assertThat(defSum.await()).isEqualTo(1 + 2 + 3 + 4 + 6 + 7 + 8 + 9 + 10)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `and coroutineScope actually allows to use more familiar try-catch logic - BUT removes parallelism`() =
        runTest {
            //let's add nested scopes, so that inner exception passes through them, now we won't have an answer.
            val defSum = async {
                val asyncs = mutableListOf<Deferred<Int>>()
                for (i in 1..10) {
                    try {
                        //ok, this coroutineScope won't kill the parent job on exception (but will still throw
                        //the exception, which can be caught) and we'll have correct result.
                        //(https://www.lukaslechner.com/why-exception-handling-with-kotlin-coroutines-is-so-hard-and-how-to-successfully-master-it/ :
                        //"So the scoping function coroutineScope{} re-throws exceptions of its failing children
                        //instead of propagating them up the job hierarchy.")

                        //But also remember coroutineScope doesn't finish until all its
                        //children finish, so there'll be no parallelism of calling unreliableFunc's.
                        //so async() is effectively useless.
                        coroutineScope {
                            asyncs.add(
                                async {
                                    unreliableFunc(i)
                                })
                        }
                    } catch (ex: Exception) {
                        println("Exception happened for i=$i, but ignoring it and continuing")
                    }
                }
                var sum = 0
                asyncs.forEachIndexed { idx, async ->
                    try {
                        val i = async.await()
                        sum += i
                    } catch (ex: Exception) {
                        println("Exception happened for i=${idx + 1}, but ignoring it and continuing: $ex")
                    }
                }
                sum
            }
            assertThat(defSum.await()).isEqualTo(1 + 2 + 3 + 4 + 6 + 7 + 8 + 9 + 10)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `we can surround coroutineScope in try catch and child exception won't cancel parent job and its other children`() =
        runTest {
            try {
                //coroutineScope means, among other, it won't go past its scope until children are done or throw.
                //note unlike pure coroutines we do NOT need supervisorScope/SupervisorJob, our parent coroutine and
                //its other children won't be cancelled
                coroutineScope {
                    launch { error("qqq") }
                }
            } catch (ex: Exception) {
                println("Exception happened, ignoring it: $ex")
            }

            delay(1000)
            println("survived crash of problem in children scope")
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `and if we don't use coroutineScope, try catch won't do anything`() {
        assertThat {
            runTest {
                try {
                    launch { error("qqq") }
                } catch (ex: Exception) {
                    println("Exception happened, ignoring it: $ex")
                }
                delay(1000)
                fail("will never get here, child coroutine will be cancelled after exception in another branch")
            }
        }.isFailure().hasMessage("qqq")
    }

}
