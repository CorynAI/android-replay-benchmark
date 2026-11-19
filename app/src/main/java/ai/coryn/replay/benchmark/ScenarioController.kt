package ai.coryn.replay.benchmark

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import kotlin.math.sin

internal class ScenarioController(private val activity: Activity, private val container: FrameLayout) {
    private val style = BenchmarkStyle(activity)
    private var list: ListView? = null
    private var animation: AnimationView? = null
    private var _frames = 0
    private val rowHeight = (72 * activity.resources.displayMetrics.density).toInt()
    val frames: Int get() = _frames

    fun show(name: String) {
        require(name in setOf("static", "list", "images", "animation"))
        list = null
        animation = null
        _frames = 0
        container.removeAllViews()
        when (name) {
            "static" -> container.addView(staticScreen())
            "list", "images" -> {
                val rows = ListView(activity).apply {
                    adapter = ScenarioListAdapter(activity, rowHeight, name == "images")
                    dividerHeight = 0
                    isVerticalScrollBarEnabled = false
                }
                list = rows
                container.addView(rows)
            }
            "animation" -> {
                val view = AnimationView(activity)
                animation = view
                container.addView(view)
            }
        }
    }

    fun frame(elapsedMs: Long) {
        _frames++
        list?.let { rows ->
            val position = ((elapsedMs % 40_000) * rowHeight / 400).toInt()
            rows.setSelectionFromTop(position / rowHeight, -(position % rowHeight))
        }
        animation?.render(elapsedMs)
    }

    private fun staticScreen(): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(style.dp(20), style.dp(24), style.dp(20), style.dp(24))
        }
        content.addView(style.text("A screen that stays still", 12f, style.muted, true))
        style.gap(content, 20)
        content.addView(style.text("Order #1042", 28f, style.ink, true))
        style.gap(content, 12)
        content.addView(style.text("Delivery tomorrow", 16f, style.muted, false))
        style.gap(content, 20)
        content.addView(style.text("3 items · €42.00", 20f, style.ink, true))
        style.gap(content, 24)
        content.addView(style.text("●  Ready for dispatch", 12f, style.green, true).apply {
            background = style.rounded(style.greenSurface, 8, Color.TRANSPARENT)
            setPadding(style.dp(12), style.dp(10), style.dp(12), style.dp(10))
        })
        return content
    }
}

internal class ScenarioListAdapter(private val activity: Activity, private val rowHeight: Int, private val images: Boolean) : BaseAdapter() {
    private val style = BenchmarkStyle(activity)
    private val palette = listOf(Color.rgb(141, 115, 183), Color.rgb(106, 141, 154), Color.rgb(190, 148, 161), Color.rgb(116, 125, 177))
    private val bitmaps = mutableListOf<Bitmap>()

    init {
        if (images) {
            repeat(8) { index ->
                val bitmap = Bitmap.createBitmap(192, 128, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(palette[index % palette.size])
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                repeat(12) { circle -> canvas.drawCircle((circle * 37 % 192).toFloat(), (circle * 23 % 128).toFloat(), 12f, paint) }
                bitmaps.add(bitmap)
            }
        }
    }

    override fun getCount() = 240
    override fun getItem(position: Int) = position
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView as? LinearLayout ?: LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(style.dp(12), 4, style.dp(12), 4)
            layoutParams = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
            if (images) {
                addView(ImageView(activity).apply {
                    background = style.rounded(Color.WHITE, 8, Color.TRANSPARENT)
                    clipToOutline = true
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }, LinearLayout.LayoutParams(rowHeight - style.dp(16), rowHeight - style.dp(16)))
            }
            addView(style.text("", 14f, style.ink, false).apply { setPadding(style.dp(12), 0, 0, 0); setLineSpacing(style.dp(5).toFloat(), 1f) })
        }
        val textIndex = if (images) 1 else 0
        (row.getChildAt(textIndex) as TextView).text = "Item ${position + 1}\nFixed benchmark data · ${position % 7 + 1} details"
        val background = if (position % 2 == 0) Color.WHITE else Color.rgb(247, 246, 251)
        row.setBackgroundColor(background)
        if (images) { (row.getChildAt(0) as ImageView).setImageBitmap(bitmaps[position % bitmaps.size]) }
        return row
    }
}

internal class AnimationView(activity: Activity) : View(activity) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var elapsedMs = 0L

    init {
        setBackgroundColor(Color.rgb(248, 247, 252))
    }

    fun render(elapsed: Long) {
        elapsedMs = elapsed
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (index in 0 until 32) {
            paint.color = Color.rgb(105 + index * 3, 80 + index * 3, 160 + index * 2)
            val fraction = ((elapsedMs + index * 190) % 6_000) / 6_000f
            val x = width * fraction
            val y = height * (0.5f + 0.4f * sin(index + elapsedMs / 900.0).toFloat())
            canvas.drawCircle(x, y, 12f + index % 5 * 4, paint)
        }
    }
}
