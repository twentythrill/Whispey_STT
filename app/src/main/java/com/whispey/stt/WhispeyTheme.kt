package com.whispey.stt

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.ContextCompat

/**
 * Resolves semantic colors for the currently active light/dark configuration.
 *
 * The UIs in this app are built programmatically (no XML layouts), so instead of
 * relying on theme attribute resolution we read the color resources directly.
 * Android automatically serves values-night/colors.xml when the context's
 * configuration is in night mode, so ContextCompat.getColor already returns the
 * correct per-mode value.
 */
object WhispeyTheme {

    fun isNight(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    fun background(context: Context) = ContextCompat.getColor(context, R.color.whispey_background)
    fun surface(context: Context) = ContextCompat.getColor(context, R.color.whispey_surface)
    fun textPrimary(context: Context) = ContextCompat.getColor(context, R.color.whispey_text_primary)
    fun textSecondary(context: Context) = ContextCompat.getColor(context, R.color.whispey_text_secondary)
    fun hint(context: Context) = ContextCompat.getColor(context, R.color.whispey_hint)
    fun error(context: Context) = ContextCompat.getColor(context, R.color.whispey_error)

    fun circleIdle(context: Context) = ContextCompat.getColor(context, R.color.whispey_circle_idle)
    fun circleRecording(context: Context) = ContextCompat.getColor(context, R.color.whispey_circle_recording)
    fun circleProcessing(context: Context) = ContextCompat.getColor(context, R.color.whispey_circle_processing)
    fun iconTint(context: Context) = ContextCompat.getColor(context, R.color.whispey_icon_tint)
}
