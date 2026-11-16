package io.github.nindzyago.torrentodrive.connection

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import io.github.nindzyago.torrentodrive.Http
import io.github.nindzyago.torrentodrive.R
import io.github.nindzyago.torrentodrive.trackers.TrackerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/** One line of the report; [ok] null = plain information, neither good nor bad. */
data class DiagnosisLine(val label: String, val text: String, val ok: Boolean?)

data class TrackerDiagnosis(val name: String, val host: String, val lines: List<DiagnosisLine>)

/**
 * Finds out why a tracker doesn't open on this network, step by step: what the provider's DNS says against an
 * independent one, whether the site answers directly, and what each byedpi strategy does on its own.
 * The report is meant to be photographed off the TV and sent to whoever can read it.
 */
object Diagnostics {
    val running = mutableStateOf(false)

    /** What is being checked right now, for the progress line. */
    val progress = mutableStateOf("")

    val report = mutableStateOf<List<TrackerDiagnosis>?>(null)

    private const val TIMEOUT = 7_000

    /** Call through [Connection.diagnose]: tracker traffic is interrupted while byedpi is restarted. */
    internal suspend fun run(context: Context, trackers: List<TrackerProvider>) {
        running.value = true
        report.value = null
        try {
            val steps = ByeDpi.STRATEGIES.size + 2
            fun step(n: Int) {
                progress.value = context.getString(R.string.diagnostics_step, n, steps)
            }

            step(1)
            val sites = trackers.map { Site(it.name, URI(it.site).host) }
            val lines = sites.associateWith { mutableListOf<DiagnosisLine>() }
            val targets = parallel(sites) { site -> resolve(context, site, lines.getValue(site)) }

            step(2)
            parallel(sites) { site ->
                val target = targets.getValue(site)
                val label = context.getString(R.string.diagnostics_direct)
                // when the provider's DNS gives other addresses, both are worth a try
                if (target.system != null && target.system != target.address) {
                    lines.getValue(site) += fetch(context, "$label → ${target.system.hostAddress}", site.host, target.system, null)
                }
                if (target.address != null) {
                    lines.getValue(site) += fetch(context, "$label → ${target.address.hostAddress}", site.host, target.address, null)
                }
            }

            ByeDpi.STRATEGIES.forEachIndexed { i, strategy ->
                step(i + 3)
                val label = strategy.joinToString(" ")
                val proxy = runCatching { ByeDpi.startOnly(strategy) }.getOrNull()
                parallel(sites) { site ->
                    val address = targets.getValue(site).address
                    lines.getValue(site) += when {
                        proxy == null -> DiagnosisLine(label, context.getString(R.string.diagnostics_not_started), false)
                        address == null -> DiagnosisLine(label, context.getString(R.string.diagnostics_no_address), false)
                        else -> fetch(context, label, site.host, address, proxy)
                    }
                }
            }
            report.value = sites.map { TrackerDiagnosis(it.name, it.host, lines.getValue(it)) }
        } finally {
            // the next connection check starts byedpi again in its normal, automatic mode;
            // also when the screen was left halfway through and this coroutine is being cancelled
            withContext(NonCancellable) { runCatching { ByeDpi.stop() } }
            running.value = false
        }
    }

    private data class Site(val name: String, val host: String)

    /** [system]: what the device's own DNS says; [address]: where to connect (the independent answer when there is one). */
    private class Target(val system: InetAddress?, val address: InetAddress?)

    private suspend fun <T> parallel(sites: List<Site>, block: suspend (Site) -> T): Map<Site, T> = coroutineScope {
        sites.map { site -> async(Dispatchers.IO) { site to block(site) } }.awaitAll().toMap()
    }

    private fun resolve(context: Context, site: Site, out: MutableList<DiagnosisLine>): Target {
        val system = runCatching { InetAddress.getAllByName(site.host).filterIsInstance<Inet4Address>() }
        val independent = runCatching { overHttps(site.host) }
        val none = context.getString(R.string.diagnostics_dns_failed)
        fun List<InetAddress>.shown() = take(3).joinToString(", ") { it.hostAddress.orEmpty() }

        val mine = system.getOrNull().orEmpty()
        val theirs = independent.getOrNull().orEmpty()
        out += DiagnosisLine(
            context.getString(R.string.diagnostics_dns_system),
            if (mine.isEmpty()) "$none ${system.exceptionOrNull()?.brief().orEmpty()}".trim() else mine.shown(),
            if (mine.isEmpty()) false else null,
        )
        val differ = mine.isNotEmpty() && theirs.isNotEmpty() && mine.none { it in theirs }
        out += DiagnosisLine(
            context.getString(R.string.diagnostics_dns_independent),
            when {
                theirs.isEmpty() -> "$none ${independent.exceptionOrNull()?.brief().orEmpty()}".trim()
                differ -> theirs.shown() + " · " + context.getString(R.string.diagnostics_dns_differ)
                else -> theirs.shown()
            },
            when {
                theirs.isEmpty() -> null
                differ -> false
                else -> true
            },
        )
        return Target(mine.firstOrNull(), theirs.firstOrNull() ?: mine.firstOrNull())
    }

    /**
     * The site's addresses from a public resolver over HTTPS (Google, then Cloudflare), asked by IP address so the
     * question doesn't depend on the DNS being checked.
     */
    private fun overHttps(host: String): List<InetAddress> {
        val client = Http.client.newBuilder().callTimeout(5, TimeUnit.SECONDS).build()
        var failure: Throwable? = null
        for (resolver in listOf("https://8.8.8.8/resolve", "https://1.1.1.1/dns-query")) {
            try {
                val request = Request.Builder().url("$resolver?name=$host&type=A").header("Accept", "application/dns-json").build()
                val json = client.newCall(request).execute().use { it.body.string() }
                return Probe.addressesIn(json).map(InetAddress::getByName)
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: IllegalStateException()
    }

    /** [Probe.fetch] in words. */
    private fun fetch(context: Context, label: String, host: String, address: InetAddress, proxy: InetSocketAddress?): DiagnosisLine {
        val o = Probe.fetch(host, address, proxy, timeout = TIMEOUT)
        if (o.failure == null) {
            return DiagnosisLine(label, context.getString(R.string.diagnostics_ok, o.status, o.bytes / 1024, o.millis.toInt()), true)
        }
        if (o.failure == Probe.Failure.WrongCertificate) {
            return DiagnosisLine(label, context.getString(R.string.diagnostics_wrong_certificate), false)
        }
        val stage = context.getString(
            when (o.stage) {
                Probe.Stage.Connect -> R.string.diagnostics_stage_connect
                Probe.Stage.Tls -> R.string.diagnostics_stage_tls
                Probe.Stage.Answer -> R.string.diagnostics_stage_answer
                Probe.Stage.Page -> R.string.diagnostics_stage_page
            }
        )
        val what = when (o.failure) {
            Probe.Failure.Timeout -> context.getString(R.string.diagnostics_timeout)
            Probe.Failure.Refused -> context.getString(R.string.diagnostics_refused)
            Probe.Failure.Reset -> context.getString(R.string.diagnostics_reset)
            Probe.Failure.Closed -> context.getString(R.string.diagnostics_closed)
            Probe.Failure.Tls -> context.getString(R.string.diagnostics_tls_error) + " (${o.detail})"
            else -> "(${o.detail})"
        }
        val after = if (o.bytes > 0) " · " + context.getString(R.string.diagnostics_received, o.status, o.bytes / 1024) else ""
        return DiagnosisLine(label, "$stage: $what$after · ${o.millis} ${context.getString(R.string.diagnostics_ms)}", false)
    }

    private fun Throwable.brief() = "(${message?.take(60) ?: javaClass.simpleName})"
}
