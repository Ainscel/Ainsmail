package com.ainscel.ainsmail.ui.debug

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 临时记帧。删掉本文件，并去掉 [MainActivity] 里的这一层即可。
 * 按钮只在点击时重组。帧回调里只加计数，不把帧率写进界面。
 */
@Composable
fun FrameJankOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    if (!activity.isDebuggable()) return
    var running by remember { mutableStateOf(FrameJankProbe.isRunning) }
    DisposableEffect(activity) {
        onDispose { FrameJankProbe.stop(activity) }
    }
    Box(modifier.zIndex(100f), contentAlignment = Alignment.TopEnd) {
        Surface(
            onClick = {
                running = if (running) {
                    FrameJankProbe.stop(activity)
                    false
                } else {
                    FrameJankProbe.start(activity)
                    true
                }
            },
            shape = CircleShape,
            color = if (running) Color(0xCCB3261E) else Color(0xCC1B3A4B),
        ) {
            Text(
                text = if (running) "停止" else "记帧",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
            )
        }
    }
}

private fun Activity.isDebuggable(): Boolean =
    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

private object FrameJankProbe {
    private const val TAG = "AinsmailFrame"
    private const val WORST = 8

    private val thread = HandlerThread("AinsmailFrame")
    private val state = State()
    private var window: Window? = null
    private var listening = false

    val isRunning: Boolean get() = listening

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        onFrame(metrics, dropped)
    }

    fun start(activity: Activity) {
        if (listening) return
        if (!thread.isAlive) thread.start()
        val host = activity.window
        synchronized(state) {
            state.reset(readRefresh(host))
        }
        host.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
        window = host
        listening = true
        Log.i(TAG, "start refreshHz=${"%.1f".format(Locale.US, state.refreshHz)}")
    }

    fun stop(activity: Activity) {
        if (!listening) return
        window?.removeOnFrameMetricsAvailableListener(listener)
        window = null
        listening = false
        val text = synchronized(state) { state.render() }
        Log.i(TAG, text)
        write(activity, text)
    }

    private fun onFrame(metrics: FrameMetrics, dropped: Int) {
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        if (total <= 0L) return
        val layout = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
        val draw = metrics.getMetric(FrameMetrics.DRAW_DURATION)
        val gpu = metrics.getMetric(FrameMetrics.GPU_DURATION)
        val swap = metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
        synchronized(state) {
            state.add(total, layout, draw, gpu, swap, dropped)
        }
    }

    private fun readRefresh(window: Window): Float {
        val rate = window.decorView.display?.refreshRate ?: 0f
        return if (rate >= 30f) rate else 60f
    }

    private fun write(activity: Activity, text: String) {
        try {
            val dir = File(activity.filesDir, "debug").apply { mkdirs() }
            File(dir, "frame-jank.log").appendText(text + "\n")
        } catch (error: Exception) {
            Log.w(TAG, "write ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private class State {
        var refreshHz = 60f
        var budgetNs = 16_666_667L
        var frames = 0
        var slow = 0
        var missed = 0
        var sumNs = 0L
        var maxNs = 0L
        val buckets = IntArray(5)
        var worstCount = 0
        val worstTotal = LongArray(WORST)
        val worstAt = LongArray(WORST)
        val worstLayout = LongArray(WORST)
        val worstDraw = LongArray(WORST)
        val worstGpu = LongArray(WORST)
        val worstSwap = LongArray(WORST)
        var origin = 0L

        fun reset(hz: Float) {
            refreshHz = hz
            budgetNs = (1_000_000_000.0 / hz).roundToLong().coerceAtLeast(1L)
            frames = 0
            slow = 0
            missed = 0
            sumNs = 0L
            maxNs = 0L
            buckets.fill(0)
            worstCount = 0
            origin = System.nanoTime()
        }

        fun add(total: Long, layout: Long, draw: Long, gpu: Long, swap: Long, dropped: Int) {
            frames += 1
            sumNs += total
            if (total > maxNs) maxNs = total
            if (dropped > 0) missed += dropped
            val ratio = total.toDouble() / budgetNs.toDouble()
            val bucket = when {
                ratio <= 1.0 -> 0
                ratio <= 1.5 -> 1
                ratio <= 2.0 -> 2
                ratio <= 4.0 -> 3
                else -> 4
            }
            buckets[bucket] += 1
            if (ratio > 1.0) slow += 1
            considerWorst(total, layout, draw, gpu, swap)
        }

        fun render(): String {
            val avg = if (frames == 0) 0.0 else sumNs.toDouble() / frames / 1_000_000.0
            val budgetMs = budgetNs / 1_000_000.0
            val slowPct = if (frames == 0) 0.0 else slow * 100.0 / frames
            val whenText = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val lines = StringBuilder()
            lines.append("session $whenText refreshHz=$refreshHz budgetMs=${num(budgetMs)}")
            lines.append("\nframes=$frames slow=$slow (${num(slowPct, 1)}%) missed=$missed")
            lines.append("\navgMs=${num(avg)} maxMs=${num(maxNs / 1_000_000.0)}")
            lines.append(
                "\nbuckets <=1x:${buckets[0]} <=1.5x:${buckets[1]} <=2x:${buckets[2]} " +
                    "<=4x:${buckets[3]} >4x:${buckets[4]}",
            )
            for (index in 0 until worstCount) {
                lines.append(
                    "\nworst +${worstAt[index]}ms total=${ms(worstTotal[index])} " +
                        "layout=${ms(worstLayout[index])} draw=${ms(worstDraw[index])} " +
                        "gpu=${ms(worstGpu[index])} swap=${ms(worstSwap[index])}",
                )
            }
            return lines.toString()
        }

        private fun considerWorst(total: Long, layout: Long, draw: Long, gpu: Long, swap: Long) {
            val at = ((System.nanoTime() - origin) / 1_000_000L).coerceAtLeast(0L)
            val limit = if (worstCount < WORST) worstCount else WORST
            var slot = limit
            while (slot > 0 && total > worstTotal[slot - 1]) slot -= 1
            if (slot == WORST) return
            val last = (worstCount).coerceAtMost(WORST - 1)
            for (index in last downTo slot + 1) {
                copyWorst(index - 1, index)
            }
            worstTotal[slot] = total
            worstAt[slot] = at
            worstLayout[slot] = layout
            worstDraw[slot] = draw
            worstGpu[slot] = gpu
            worstSwap[slot] = swap
            if (worstCount < WORST) worstCount += 1
        }

        private fun copyWorst(from: Int, to: Int) {
            worstTotal[to] = worstTotal[from]
            worstAt[to] = worstAt[from]
            worstLayout[to] = worstLayout[from]
            worstDraw[to] = worstDraw[from]
            worstGpu[to] = worstGpu[from]
            worstSwap[to] = worstSwap[from]
        }

        private fun ms(nanos: Long): String = num(nanos / 1_000_000.0)

        private fun num(value: Double, digits: Int = 2): String =
            "%.${digits}f".format(Locale.US, value)
    }
}
