package io.olcos.app.core

import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Which lane takes the next connection: round robin over the lanes that are
 * up and not benched; when none qualifies, over the ones that are up; when
 * none is up, still in turn. A lane is benched for a while after it twice in
 * a row returned a connection that died with almost nothing sent back: a room
 * that lost its server still accepts SOCKS, but every CONNECT through it fails.
 */
class LanePolicy(count: Int) {
    val count: Int = count.coerceAtLeast(1)
    private var next = 0
    private val failures = IntArray(this.count)
    private val benchedUntil = LongArray(this.count)

    @Synchronized
    fun pick(isUp: (Int) -> Boolean, now: Long): Int {
        val order = (0 until count).map { (next + it) % count }
        val rested = order.firstOrNull { isUp(it) && benchedUntil[it] <= now }
        val lane = rested ?: order.firstOrNull(isUp) ?: order[0]
        next = (lane + 1) % count
        return lane
    }

    @Synchronized
    fun record(lane: Int, dead: Boolean, now: Long) {
        if (lane !in 0 until count) return
        if (dead) {
            failures[lane]++
            if (failures[lane] >= 2) {
                benchedUntil[lane] = now + BENCH_MS
                failures[lane] = 0
            }
        } else {
            failures[lane] = 0
            benchedUntil[lane] = 0
        }
    }

    /**
     * The lane's own listener is gone, or its connections hang: every
     * connection to it fails the same way, so it is set aside at once instead
     * of after two of them.
     */
    @Synchronized
    fun recordGone(lane: Int, now: Long) {
        if (lane !in 0 until count) return
        failures[lane] = 0
        benchedUntil[lane] = now + BENCH_MS
    }

    companion object {
        const val BENCH_MS = 20_000L
        /** A connection with no byte back after this long is stuck, not slow: not even the SOCKS reply came. */
        const val HUNG_AFTER_MS = 60_000L

        /** Closed within a few seconds with no more than a SOCKS reply's worth of bytes back. */
        fun looksDead(bytesBack: Long, durationMs: Long): Boolean = bytesBack <= 32 && durationMs < 5_000

        fun looksHung(bytesBack: Long, waitedMs: Long): Boolean = bytesBack == 0L && waitedMs >= HUNG_AFTER_MS
    }
}

/**
 * Spreads one tunnel's connections over several rooms (lanes) when the server
 * runs no bond endpoint. Each lane is its own core runtime with its own
 * loopback SOCKS listener; this listens on the port apps use and hands every
 * new connection to the next lane that is up, byte for byte, so the SOCKS
 * handshake itself goes through to the lane. The same policy as the iOS
 * LaneBalancer.
 */
class LaneBalancer(
    private val port: Int,
    private val lanePorts: List<Int>,
    private val isUp: (Int) -> Boolean,
) {
    private val policy = LanePolicy(lanePorts.size)
    @Volatile private var server: ServerSocket? = null

    /** Throws when the port is taken. */
    fun start() {
        val listener = ServerSocket(port, 64, Loopback.address)
        server = listener
        thread(name = "olcos-balancer", isDaemon = true) {
            while (true) {
                val client = try {
                    listener.accept()
                } catch (e: Exception) {
                    break
                }
                thread(name = "olcos-lane-conn", isDaemon = true) { serve(client) }
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun serve(client: Socket) {
        val lane = policy.pick(isUp, System.currentTimeMillis())
        val started = System.currentTimeMillis()
        val upstream = try {
            Socket(Loopback.address, lanePorts[lane])
        } catch (e: Exception) {
            // The lane's listener is gone: set it aside now, and end this connection so the app retries on another.
            runCatching { client.close() }
            policy.recordGone(lane, System.currentTimeMillis())
            return
        }
        runCatching { client.tcpNoDelay = true }
        runCatching { upstream.tcpNoDelay = true }
        val back = AtomicLong()
        val hung = AtomicBoolean()
        // Not even the SOCKS reply after a minute: the lane is stuck. Set it aside and end the connection.
        val watchdog = hangWatch.schedule({
            if (LanePolicy.looksHung(back.get(), System.currentTimeMillis() - started)) {
                hung.set(true)
                policy.recordGone(lane, System.currentTimeMillis())
                runCatching { client.close() }
                runCatching { upstream.close() }
            }
        }, LanePolicy.HUNG_AFTER_MS, TimeUnit.MILLISECONDS)
        val down = thread(name = "olcos-lane-down", isDaemon = true) {
            pump(upstream.getInputStream(), client.getOutputStream(), back)
            runCatching { client.shutdownOutput() }
        }
        pump(client.getInputStream(), upstream.getOutputStream(), AtomicLong())
        runCatching { upstream.shutdownOutput() }
        down.join()
        watchdog.cancel(false)
        runCatching { client.close() }
        runCatching { upstream.close() }
        if (hung.get()) return
        val now = System.currentTimeMillis()
        policy.record(lane, LanePolicy.looksDead(back.get(), now - started), now)
    }

    private fun pump(from: InputStream, to: OutputStream, counter: AtomicLong) {
        val buffer = ByteArray(32 * 1024)
        try {
            while (true) {
                val n = from.read(buffer)
                if (n < 0) break
                to.write(buffer, 0, n)
                to.flush()
                counter.addAndGet(n.toLong())
            }
        } catch (e: Exception) {
            // The other side went away; the caller closes both sockets.
        }
    }

    private companion object {
        /** One timer for every connection's hang check. */
        val hangWatch = ScheduledThreadPoolExecutor(1) { r ->
            Thread(r, "olcos-lane-hang").apply { isDaemon = true }
        }.also { it.removeOnCancelPolicy = true }
    }
}
