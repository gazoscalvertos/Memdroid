package com.memdroid.app.data

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_RUNNING, NO_PERMISSION, READY }

data class ShellResult(val code: Int, val out: String, val err: String) {
    val ok get() = code == 0
}

/**
 * Runs shell commands as the ADB shell user through Shizuku, which is what lets
 * Memdroid list and force-stop other apps without root.
 */
object ShizukuShell {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4242

    private val _state = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val state: StateFlow<ShizukuState> = _state

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        _state.value = try {
            when {
                !Shizuku.pingBinder() || Shizuku.isPreV11() -> ShizukuState.NOT_RUNNING
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
                else -> ShizukuState.NO_PERMISSION
            }
        } catch (e: Exception) {
            ShizukuState.NOT_RUNNING
        }
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Exception) {
            refresh()
        }
    }

    // Shizuku.newProcess is hidden in API 13 but still the simplest way to run a shell command.
    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (_state.value != ShizukuState.READY) {
            return@withContext ShellResult(-1, "", "Shizuku is not ready")
        }
        try {
            val process = newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            ShellResult(process.waitFor(), out, err)
        } catch (e: Exception) {
            ShellResult(-1, "", e.cause?.message ?: e.message ?: e.toString())
        }
    }
}
