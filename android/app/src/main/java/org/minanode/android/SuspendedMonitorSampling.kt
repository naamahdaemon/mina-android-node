package org.minanode.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** Installed only by the monitor page. Never starts a service or resumes Mina. */
@Composable
fun SuspendedMonitorSampling(suspended: Boolean) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(suspended, owner) {
        if (suspended) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                NodeService.sampleSuspendedResources()
                delay(2000)
            }
        }
    }
}
