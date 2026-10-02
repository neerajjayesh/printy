// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app

import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.printy.app.data.PrinterProfile
import org.printy.app.printing.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PrintScreenTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PrintyApplication
    private val main get() = InstrumentationRegistry.getInstrumentation()

    private fun submit(gate: CompletableDeferred<LocalDocument>): String {
        val id = UUID.randomUUID().toString()
        main.runOnMainSync {
            app.jobs.submit(id, "Power test", PrinterProfile(name = "Test only", host = "127.0.0.1"),
                PrintSettings(), prepare = { gate.await() })
        }
        return id
    }

    private fun cancel(id: String) {
        main.runOnMainSync { app.jobs.cancel(id) }
        runBlocking { withTimeout(5000) { app.jobs.states.first { states -> states.any { it.id == id && !it.active } } } }
    }

    private fun assertScreenFlag(scenario: ActivityScenario<MainActivity>, expected: Boolean) = runBlocking {
        withTimeout(5000) {
            while (true) {
                var actual = !expected
                scenario.onActivity { actual = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 }
                if (actual == expected) break
                delay(25)
            }
        }
    }

    @Test fun activeJobKeepsScreenOnAcrossRecreationAndCancellationClearsIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertScreenFlag(scenario, false)
            val id = submit(CompletableDeferred())
            try {
                assertScreenFlag(scenario, true)
                scenario.recreate()
                assertScreenFlag(scenario, true)
                cancel(id)
                assertScreenFlag(scenario, false)
            } finally { cancel(id); main.runOnMainSync { app.jobs.dismiss(id) } }
        }
    }

    @Test fun failureWhileActivityIsStoppedClearsFlagOnReturn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gate = CompletableDeferred<LocalDocument>()
            val id = submit(gate)
            try {
                assertScreenFlag(scenario, true)
                scenario.moveToState(Lifecycle.State.CREATED)
                gate.completeExceptionally(UserPrintException("Test document unavailable"))
                runBlocking { withTimeout(5000) { app.jobs.states.first { states -> states.any { it.id == id && it.phase == JobPhase.FAILED } } } }
                scenario.moveToState(Lifecycle.State.RESUMED)
                assertScreenFlag(scenario, false)
            } finally { cancel(id); main.runOnMainSync { app.jobs.dismiss(id) } }
        }
    }

    @Test fun queuedJobKeepsScreenOnUntilTheEntireQueueStops() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val first = submit(CompletableDeferred())
            val second = submit(CompletableDeferred())
            try {
                assertScreenFlag(scenario, true)
                cancel(first)
                assertScreenFlag(scenario, true)
                cancel(second)
                assertScreenFlag(scenario, false)
            } finally {
                cancel(first); cancel(second)
                main.runOnMainSync { app.jobs.dismiss(first); app.jobs.dismiss(second) }
            }
        }
    }
}
