package ai.coryn.replay.benchmark

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal class BenchmarkStyle(private val activity: Activity) {
    val ink = Color.rgb(40, 44, 62)
    val navy = Color.rgb(28, 36, 64)
    val muted = Color.rgb(100, 101, 120)
    val border = Color.rgb(225, 227, 234)
    val lavender = Color.rgb(237, 231, 247)
    val purple = Color.rgb(112, 83, 158)
    val green = Color.rgb(44, 112, 92)
    val greenSurface = Color.rgb(235, 245, 240)

    fun page(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(234, 237, 248), Color.rgb(226, 222, 243)))
    }

    fun card(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, 16, border)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        val family = if (bold) "sans-serif-medium" else "sans-serif"
        typeface = Typeface.create(family, Typeface.NORMAL)
        includeFontPadding = false
    }

    fun button(value: String, primary: Boolean): Button = Button(activity).apply {
        text = value
        textSize = 14f
        isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minHeight = dp(48)
        minimumHeight = dp(48)
        minWidth = 0
        minimumWidth = 0
        gravity = Gravity.CENTER
        stateListAnimator = null
        val colors = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        val textColor = if (primary) Color.WHITE else ink
        setTextColor(ColorStateList(colors, intArrayOf(muted, textColor)))
        val fill = if (primary) navy else Color.WHITE
        val stroke = if (primary) navy else border
        val ripple = if (primary) Color.argb(40, 255, 255, 255) else Color.argb(35, 112, 83, 158)
        background = RippleDrawable(ColorStateList.valueOf(ripple), rounded(fill, 12, stroke), null)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    fun rounded(color: Int, radius: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    fun gap(parent: LinearLayout, height: Int) {
        parent.addView(View(activity), LinearLayout.LayoutParams(1, dp(height)))
    }

    fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
