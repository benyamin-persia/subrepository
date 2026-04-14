package com.example.factcheckoverlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.getSystemService

class OverlayController(private val context: Context) {
    private val windowManager = context.getSystemService<WindowManager>()
    private var overlayView: View? = null

    fun ensureShown() {
        if (!Settings.canDrawOverlays(context) || overlayView != null || windowManager == null) {
            return
        }

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_fact_check, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        view.setOnClickListener {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    fun showPermissionNeeded() {
        ensureShown()
        update(
            appName = "Permission needed",
            status = "Allow display over other apps so the fact-check card can appear.",
            summary = "Tap this card, then enable overlay permission.",
            sources = ""
        )
    }

    fun update(appName: String, status: String, summary: String, sources: String) {
        ensureShown()
        val view = overlayView ?: return
        view.findViewById<TextView>(R.id.overlayAppText).text = appName
        view.findViewById<TextView>(R.id.overlayStatusText).text = status
        view.findViewById<TextView>(R.id.overlaySummaryText).text = summary
        view.findViewById<TextView>(R.id.overlaySourcesText).text = sources
    }

    fun hide() {
        val view = overlayView ?: return
        windowManager?.removeView(view)
        overlayView = null
    }
}
