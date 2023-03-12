package ru.iliks.coroutines

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

class TestCoroutineContexts {
    @Test
    fun simpleContext() = runBlocking(EmptyCoroutineContext) {
        print("on empty ctx")
        withContext(CoroutineName("ctx1")) {
            print("on named context 1")
            withContext(CoroutineName("ctx2")) {
                coroutineScope {
                    print("on inner context")
                    val def1 = async(CoroutineName("delay1")) {
                        delay(1000)
                        print("from delay1")
                    }
                    val def2 = async(CoroutineName("delay2")) {
                        delay(1000)
                        print("from delay2")
                    }
                    awaitAll(def1, def2)
                    print("on inner context after all delays")
                }
            }
            print("on context 1 again")
        }
        print("on empty ctx again")
    }

    private suspend fun print(msg: String) {
        //1. context in "pure" coroutines (i.e. just suspend fun) without explicit context is stored
        //in this "well known" property coroutineContext which is native. (remember that a suspend fun
        //is transformed into a regular fun which gets additional argument, object of auto generated continuation class.
        //this continuation obj stores the current "step num" inside the suspend fun and the context)
        //2. when class name is alone, like CoroutineName, the class's companion object is returned
        val name = coroutineContext[CoroutineName]
        println("[$name] $msg")
    }
}