package io.olcos.app.core

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import io.olcos.app.AppGraph
import io.olcos.app.R
import io.olcos.app.data.TunnelMode
import io.olcos.app.model.ConnectionRecord
import kotlin.concurrent.thread

/**
 * Proxy mode: a foreground service that keeps the process, and the core's
 * SOCKS5 listener on 127.0.0.1, alive while the user keeps it on. A session
 * that is lost (no answer through it, another network, every room gone) is
 * joined again and, when it keeps failing, moved to another connection
 * (Recovery), as the iOS app does.
 */
class ProxyService : Service() {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var worker: Thread? = null
    /** Set once the service is on its way out, so a start that ends late reports nothing. */
    @Volatile private var stopping = false
    /** Bumped by every start: a start that a newer one (another connection) overtook reports nothing. */
    @Volatile private var generation = 0
    /** The recovery under way after a lost session; null for a start the person asked for. */
    private var recovery: Recovery.Run? = null
    private var keeper: SessionKeeper? = null
    /** Where one recovery leaves its count for the next: a session that held a minute starts it over. */
    private val carry = Recovery.Carry()
    /** The connection this service runs now (main thread). */
    private var current: ConnectionRecord? = null
    private var network: NetworkWatch? = null
    private var sweeps: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TunnelManager.ACTION_STOP) {
            // The notification's Disconnect comes here too: a sticky restart must not rejoin.
            AppGraph.settings.rememberSession(null)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            Notifications.startForeground(this, getString(R.string.notification_connecting))
        } catch (e: Exception) {
            // The system would not let the service into the foreground from here
            // (a restart in the background): it ends, and the app starts it once open.
            AppLog.w("olcos.service", "not allowed in the foreground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        val named = TunnelManager.recordFrom(intent)
        if (named == null) {
            // The system restarted the service after the process was killed: a session was wanted and is gone.
            val settings = AppGraph.settings
            if (KillSignal.wasKilled(settings.rememberedRecordId != null, restartedBySystem = true, alwaysOn = false,
                    sameBoot = true, updatedSince = false, serviceRunning = false)) settings.noteKilled()
        }
        val record = named ?: rememberedRecord() ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        recovery = null
        if (network == null) network = NetworkWatch(this, ::onNetwork).also { it.start() }
        begin(record)
        return START_STICKY
    }

    /**
     * A network that came back or changed joins the rooms again after a short
     * settle (a hand-over sends several updates); with none, a live session
     * is held as lost and the recovery waits for one without spending tries.
     */
    private fun onNetwork(action: NetworkPath.Action) {
        val record = current ?: return
        if (stopping) return
        when (action) {
            NetworkPath.Action.NONE -> Unit
            NetworkPath.Action.HOLD -> if (keeper != null) lost(record, "no network")
            NetworkPath.Action.REJOIN -> {
                val settle = ReconnectPolicy.networkSettleMs(Math.random())
                val run = recovery
                if (keeper != null) {
                    lost(record, "the network changed", firstDelayMs = settle, fresh = true)
                } else if (run != null) {
                    run.restart()
                    retryAfter(settle, run.record)
                }
            }
        }
    }

    /** Joins [record]'s rooms and proves the session; on the main thread. */
    private fun begin(start: ConnectionRecord) {
        // A recovery joins the connection as it is now: a room renewed or a key
        // rotated while the session was down is in the store, not in the copy.
        val record = if (recovery != null) AppGraph.store.records.value.firstOrNull { it.id == start.id } ?: start else start
        current = record
        TunnelManager.noteStarted(record, TunnelMode.PROXY, recovering = recovery != null)
        val settings = AppGraph.settings
        val myGeneration = ++generation
        keeper?.stop()
        keeper = null
        worker?.interrupt()
        SessionWatch.reset()
        OlcrtcEngine.onLanesUp = { up ->
            if (generation == myGeneration && SessionWatch.lanesUp(up)) {
                main.post { if (generation == myGeneration && !stopping) lost(record, getString(R.string.state_rooms_gone)) }
            }
        }
        worker = thread(name = "olcos-proxy") {
            try {
                val session = Engines.start(record, { !stopping && generation == myGeneration }, OlcrtcEngine.StartSettings(
                    port = settings.socksPort.value, dns = settings.dns.value,
                    deviceID = settings.deviceID, vpn = false,
                    directRules = settings.directRules,
                    socksUser = settings.socksUser.value, socksPass = settings.socksPass.value,
                    vp8FPS = settings.vp8FPS.value, vp8Batch = settings.vp8Batch.value))
                // The room answering is not enough: only a request that comes back counts.
                if (!TunnelProbe.verify(session.port, session.socksUser, session.socksPass)) {
                    // A newer start owns the engine by now: its session is not this one's to stop.
                    if (generation == myGeneration) Engines.stop()
                    throw IllegalStateException(getString(R.string.state_no_traffic))
                }
                main.post {
                    if (stopping || generation != myGeneration) return@post
                    carry.connected(SystemClock.elapsedRealtime(), recovery?.attempt ?: 0)
                    recovery = null
                    TunnelManager.reportSession(session)
                    TunnelManager.report(TunnelManager.State.Connected)
                    Notifications.update(this, getString(R.string.notification_connected, servicesLine(record)))
                    // A phone kept connected with no screen open still renews and rotates rooms (BackgroundSweeps).
                    if (sweeps == null) sweeps = BackgroundSweeps.start(applicationContext) { !stopping }
                    keeper = SessionKeeper(session, received = { null }) { reason ->
                        if (generation == myGeneration && !stopping) lost(record, reason)
                    }.also { it.start() }
                }
            } catch (e: OlcrtcEngine.Cancelled) {
                // A stop overtook the start: the service is already on its way out.
            } catch (e: Exception) {
                main.post {
                    if (stopping || generation != myGeneration) return@post
                    failed(e)
                }
            }
        }
    }

    /**
     * A live session stopped carrying traffic: join the rooms again after the
     * first wait ([firstDelayMs] when a network change sets it; [fresh]: from
     * a count of zero, since the old network's tries say nothing of the new).
     */
    private fun lost(record: ConnectionRecord, reason: String, firstDelayMs: Long? = null, fresh: Boolean = false) {
        AppLog.w("olcos.proxy", "session lost ($reason): joining the rooms again")
        keeper?.stop()
        keeper = null
        val start = if (fresh) 0 else carry.nextStart(SystemClock.elapsedRealtime())
        val run = Recovery.Run(record, startAttempt = start).also { recovery = it }
        TunnelManager.report(TunnelManager.State.Reconnecting)
        Notifications.update(this, getString(R.string.state_reconnecting))
        retryAfter(firstDelayMs ?: run.firstDelay(), run.record)
    }

    /**
     * A start that did not come up. One the person asked for is shown at
     * once; one of a recovery is tried again for as long as the person wants
     * the connection (another connection stands in once this one has had its
     * tries). Only an error that can only come again, a wrong key or a room
     * that is gone, stops it, and its real words are shown.
     */
    private fun failed(e: Exception) {
        val run = recovery
        if (run == null) {
            TunnelManager.report(TunnelManager.State.Failed(UserFacing.describe(this, e)))
            stopSelf()
            return
        }
        val raw = e.message ?: e.javaClass.simpleName
        val delay = run.afterFailure(Recovery.networkUp(this), raw) { current, tried -> nextCandidate(current, tried) }
        if (delay == null) {
            recovery = null
            AppLog.w("olcos.proxy", "recovery stopped, it cannot succeed: $raw")
            TunnelManager.report(TunnelManager.State.Failed(UserFacing.describe(this, e)))
            stopSelf()
            return
        }
        retryAfter(delay, run.record)
    }

    private fun retryAfter(delayMs: Long, record: ConnectionRecord) {
        val token = ++generation
        main.postDelayed({
            if (!stopping && generation == token) begin(record)
        }, delayMs)
    }

    private fun nextCandidate(current: ConnectionRecord, tried: Set<String>): ConnectionRecord? {
        if (!AppGraph.settings.autoFailover.value) return null
        return Recovery.candidates(current, AppGraph.store.records.value, AppGraph.servers.servers.value, Diagnostics.results.value)
            .firstOrNull { it.id !in tried }
    }

    /** The record the user last connected, for a start the system makes on its own. */
    private fun rememberedRecord(): ConnectionRecord? {
        val id = AppGraph.settings.rememberedRecordId ?: return null
        return AppGraph.store.records.value.firstOrNull { it.id == id }
    }

    private fun servicesLine(record: ConnectionRecord): String =
        record.olcrtc?.servicesLine ?: getString(R.string.engine_xray)

    override fun onDestroy() {
        stopping = true
        worker = null
        network?.stop()
        network = null
        sweeps?.cancel()
        sweeps = null
        keeper?.stop()
        keeper = null
        main.removeCallbacksAndMessages(null)
        OlcrtcEngine.onLanesUp = null
        // The engine's stop waits for the rooms to close (seconds): never on the main thread.
        Engines.stopInBackground()
        TunnelManager.noteStopped()
        super.onDestroy()
    }
}
