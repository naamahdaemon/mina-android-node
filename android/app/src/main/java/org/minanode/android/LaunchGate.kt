package org.minanode.android

import java.util.UUID

/** A launch uses the exact profile confirmed by the UI, never a second prefs read. */
class AuthorizedLaunch(val profile: NodeProfile, val password: CharArray?) : AutoCloseable {
    override fun close() { password?.fill('\u0000') }
}

/** Only an opaque one-use id goes in the Intent. Credentials remain in memory. */
object LaunchGate {
    private data class Pending(val id: String, val launch: AuthorizedLaunch, val created: Long)
    private var pending: Pending? = null

    @Synchronized fun offer(profile: NodeProfile, password: CharArray?, now: Long = System.nanoTime()): String {
        requireUi(!profile.producer || password != null, R.string.error_password_required)
        clear()
        val id = UUID.randomUUID().toString()
        pending = Pending(id, AuthorizedLaunch(profile, password), now)
        return id
    }

    @Synchronized fun take(id: String?, now: Long = System.nanoTime()): AuthorizedLaunch? {
        val request = pending ?: return null
        if (id == null || request.id != id) return null
        pending = null
        if (now - request.created !in 0..60_000_000_000L) {
            request.launch.close()
            return null
        }
        return request.launch
    }

    @Synchronized fun revoke(id: String?) { if (id != null && pending?.id == id) clear() }
    @Synchronized fun clear() { pending?.launch?.close(); pending = null }
}
