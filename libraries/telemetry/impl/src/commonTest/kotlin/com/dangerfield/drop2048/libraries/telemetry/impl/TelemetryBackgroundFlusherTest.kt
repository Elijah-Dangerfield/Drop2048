package com.dangerfield.drop2048.libraries.telemetry.impl

import com.dangerfield.drop2048.libraries.drop2048.AppEvent
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TelemetryBackgroundFlusherTest : CoroutineTest() {

    private class FlushCountingProcessor : LogRecordProcessor {
        var flushCount = 0
        override fun onEmit(log: ReadWriteLogRecord, context: Context) = Unit
        override suspend fun forceFlush(): OperationResultCode {
            flushCount++
            return OperationResultCode.Success
        }

        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    private val processor = FlushCountingProcessor()

    @AfterTest
    fun tearDown() {
        KLog.clearTrees()
    }

    private fun plantTree(): GrafanaLogTree {
        val tree = GrafanaLogTree(
            exportEnabled = { true },
            sampleRate = { 1.0 },
            klogForwardingEnabled = { false },
            currentSessionId = { "session-uuid-1" },
            currentInstallId = { "install-uuid-1" },
            isOffline = { false },
            installFacts = { RetailInstallFacts },
            processorFactory = { processor },
        )
        KLog.plant(tree)
        return tree
    }

    @Test
    fun backgroundEdge_flushesThePlantedTree() = runUnitTest {
        val flusher = TelemetryBackgroundFlusher(AppCoroutineScope(dispatchers))
        flusher.onBackground(AppEvent.OnBackground)

        flusher.tree = plantTree()
        KLog.logEvent("example.completed")
        flusher.onBackground(AppEvent.OnBackground)

        assertEquals(1, processor.flushCount)
    }

    @Test
    fun flush_neverConstructsAnSdkThatNothingLoggedThrough() = runUnitTest {
        val flusher = TelemetryBackgroundFlusher(AppCoroutineScope(dispatchers))
        flusher.tree = plantTree()

        flusher.onBackground(AppEvent.OnBackground)

        assertEquals(0, processor.flushCount)
    }
}
