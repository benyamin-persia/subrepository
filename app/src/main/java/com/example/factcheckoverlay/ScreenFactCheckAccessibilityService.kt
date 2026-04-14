package com.example.factcheckoverlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class ScreenFactCheckAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var overlayController: OverlayController
    private lateinit var repository: FactCheckRepository
    private lateinit var preferences: AppPreferences

    private var lastFingerprint = ""
    private var pendingJob: Job? = null
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayController = OverlayController(this)
        preferences = AppPreferences(this)
        repository = FactCheckRepository(preferences)

        if (preferences.baseUrl.isBlank()) {
            overlayController.update(
                appName = "Setup required",
                status = "Add backend URL in app settings.",
                summary = "The overlay is ready, but it needs a fact-check backend endpoint first.",
                sources = ""
            )
        } else {
            overlayController.ensureShown()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString().orEmpty()
        if (packageName.isBlank() || packageName == this.packageName) {
            return
        }

        if (packageName !in TARGET_PACKAGES) {
            pendingJob?.cancel()
            overlayController.hide()
            return
        }

        if (!::overlayController.isInitialized) {
            return
        }

        if (preferences.baseUrl.isBlank()) {
            overlayController.update(
                appName = packageName,
                status = "Backend not configured.",
                summary = "Open the app and set the live fact-check endpoint first.",
                sources = ""
            )
            return
        }

        val root = rootInActiveWindow ?: return
        val visibleText = extractVisibleText(root)
        pendingJob?.cancel()
        pendingJob = serviceScope.launch {
            overlayController.update(
                appName = packageName,
                status = "Screen changed. Waiting for it to settle...",
                summary = preview(visibleText),
                sources = ""
            )
            delay(2200)

            val latestVisibleText = rootInActiveWindow
                ?.let(::extractVisibleText)
                ?.takeIf { it.isNotBlank() }
                ?: visibleText

            val candidateText = latestVisibleText.takeIf { it.length >= 80 }
                ?: captureOcrText().takeIf { it.length >= 80 }
                ?: latestVisibleText

            if (candidateText.length < 80) {
                return@launch
            }

            val fingerprint = candidateText.lowercase()
                .replace(Regex("\\s+"), " ")
                .take(500)

            if (fingerprint == lastFingerprint) {
                return@launch
            }

            lastFingerprint = fingerprint
            runFactCheck(packageName, candidateText)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pendingJob?.cancel()
        overlayController.hide()
        recognizer.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun runFactCheck(packageName: String, text: String) {
        overlayController.update(
            appName = packageName,
            status = "Fact-checking live web sources...",
            summary = preview(text),
            sources = ""
        )

        val result = repository.check(text)
        result.onSuccess { response ->
            val sources = response.sources.take(2).joinToString("\n") { "• ${it.title}" }
            overlayController.update(
                appName = packageName,
                status = "${response.verdict} (${(response.confidence * 100).toInt()}%)",
                summary = response.summary,
                sources = sources
            )
        }.onFailure { error ->
            overlayController.update(
                appName = packageName,
                status = "Fact-check failed",
                summary = error.message ?: "Unknown error",
                sources = ""
            )
        }
    }

    private fun extractVisibleText(node: AccessibilityNodeInfo): String {
        val lines = linkedSetOf<String>()

        fun walk(current: AccessibilityNodeInfo?) {
            if (current == null) return

            current.text?.toString()?.trim()?.takeIf { it.length > 1 }?.let(lines::add)
            current.contentDescription?.toString()?.trim()?.takeIf { it.length > 1 }?.let(lines::add)

            for (index in 0 until current.childCount) {
                walk(current.getChild(index))
            }
        }

        walk(node)
        return lines.joinToString("\n").take(6000)
    }

    private fun preview(text: String): String {
        return text.replace(Regex("\\s+"), " ").take(220)
    }

    private suspend fun captureOcrText(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ""
        }

        val bitmap = takeAccessibilityScreenshot() ?: return ""
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.text.trim().take(6000)
    }

    private suspend fun takeAccessibilityScreenshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshotResult.hardwareBuffer,
                            screenshotResult.colorSpace
                        )?.copy(Bitmap.Config.ARGB_8888, false)
                        screenshotResult.hardwareBuffer.close()
                        continuation.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        continuation.resume(null)
                    }
                }
            )
        }
    }

    companion object {
        private val TARGET_PACKAGES = setOf(
            "com.twitter.android"
        )
    }
}
