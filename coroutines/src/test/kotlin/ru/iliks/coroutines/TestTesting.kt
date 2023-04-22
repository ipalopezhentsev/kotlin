package ru.iliks.coroutines

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test

class TestTesting {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test manual control of virtual time`() = runBlocking {
        val scope = TestScope()
        val parentJob = scope.launch {
            coroutineScope {
                launch { delay(1000) }
                launch { delay(1000) }
            }
        }
        scope.advanceTimeBy(500)
        assertThat(parentJob.isActive).isTrue()
        assertThat(parentJob.isCompleted).isFalse()
        scope.advanceUntilIdle()
        assertThat(parentJob.isActive).isFalse()
        assertThat(parentJob.isCompleted).isTrue()
        assertThat(scope.currentTime).isEqualTo(1000)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test auto advancing virtual time to the end`() = runTest {
        val parentJob = coroutineScope {
            launch { delay(1000) }
            launch { delay(1000) }
        }
        assertThat(parentJob.isActive).isFalse()
        assertThat(parentJob.isCompleted).isTrue()
        assertThat(currentTime).isEqualTo(1000)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test mockk coEvery`() = runTest {
        val mockedRepo = mockk<UserRepository>()
        coEvery { mockedRepo.loadUser("user") } coAnswers { "123" }
        val user = mockedRepo.loadUser("user")
        assertThat(user).isEqualTo("123")
    }
}

interface UserRepository {
    suspend fun loadUser(id: String): String
}