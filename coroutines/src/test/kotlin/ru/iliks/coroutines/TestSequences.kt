package ru.iliks.coroutines

import org.junit.jupiter.api.Test

class TestSequences {
    @Test
    fun testSeq() {
        val nums = sequence {
            yield(1)
            yield(2)
        }
        for (i in nums) {
            println(i)
        }
    }
}