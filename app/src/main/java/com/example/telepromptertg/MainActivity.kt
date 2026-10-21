package com.example.telepromptertg

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class MainActivity : Activity() {

    companion object {
        private const val STATE_PAGE = "selected_page"
    }

    private lateinit var prefs: Prefs
    private lateinit var scriptEdit: EditText
    private lateinit var wpmValueTv: TextView
    private lateinit var sizeValueTv: TextView
    private lateinit var permBtn: Button
    private lateinit var startBtn: Button
    private lateinit var updateBtn: Button
    private lateinit var pages: List<ScrollView>
    private lateinit var navButtons: List<Button>
    private lateinit var content: FrameLayout
    private lateinit var swipeDetector: GestureDetector
    private var currentPage = 0
    private var downloading = false

    private val handler = Handler(Looper.getMainLooper())
    private var pendingPush: Runnable? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        setTheme(
            if (prefs.darkMode) {
                android.R.style.Theme_DeviceDefault_NoActionBar
            } else {
                android.R.style.Theme_DeviceDefault_Light_NoActionBar
            },
        )
        super.onCreate(savedInstanceState)
        currentPage = savedInstanceState?.getInt(STATE_PAGE, 0)?.coerceIn(0, 2) ?: 0

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        root.addView(label("Teleprompter", 26f, true))
        root.addView(label(getString(R.string.version_label, currentVersion()), 12f, false))

        val scriptPage = page().apply {
            addView(label("Read your script while recording videos with your camera app.", 14f, false))
            addView(label("Script", 18f, true), spaced(16))
        }
        scriptEdit = EditText(this).apply {
            hint = "Enter or paste your text / script here..."
            setText(prefs.text)
            minLines = 8
            maxLines = 16
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                val fill = if (prefs.darkMode) 255 else 0
                setColor(Color.argb(20, fill, fill, fill))
                setStroke(dp(1), Color.GRAY)
                cornerRadius = dp(8).toFloat()
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val text = s?.toString() ?: ""
                    prefs.text = text
                    schedulePush()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        scriptPage.addView(scriptEdit, spaced(8))
        startBtn = button(getString(R.string.start_teleprompter)) { toggleTeleprompter() }
        scriptPage.addView(startBtn, spaced(16))

        val settingsPage = page()
        settingsPage.addView(label("Playback", 18f, true))
        settingsPage.addView(label("Scroll Speed (WPM)", 16f, true), spaced(16))
        val wpmRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val wpmMinusBtn = button("−10") { changeWpm(-10) }
        wpmValueTv = label(getString(R.string.wpm_value, prefs.wpm), 16f, true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val wpmPlusBtn = button("+10") { changeWpm(10) }
        wpmRow.addView(wpmMinusBtn, flexBtnParams())
        wpmRow.addView(wpmValueTv)
        wpmRow.addView(wpmPlusBtn, flexBtnParams())
        settingsPage.addView(wpmRow, spaced(6))

        settingsPage.addView(label("Font Size", 16f, true), spaced(16))
        val sizeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sizeMinusBtn = button("A−") { changeSize(-2) }
        sizeValueTv = label(getString(R.string.size_value, prefs.size), 16f, true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sizePlusBtn = button("A+") { changeSize(2) }
        sizeRow.addView(sizeMinusBtn, flexBtnParams())
        sizeRow.addView(sizeValueTv)
        sizeRow.addView(sizePlusBtn, flexBtnParams())
        settingsPage.addView(sizeRow, spaced(6))

        settingsPage.addView(label("Appearance", 18f, true), spaced(24))
        settingsPage.addView(Switch(this).apply {
            setText(R.string.dark_mode)
            isChecked = prefs.darkMode
            setOnCheckedChangeListener { _, enabled ->
                prefs.darkMode = enabled
                recreate()
            }
        }, spaced(8))

        settingsPage.addView(label("Permissions and updates", 18f, true), spaced(24))
        permBtn = Button(this).apply {
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                )
            }
        }
        settingsPage.addView(permBtn, spaced(8))

        updateBtn = Button(this).apply {
            setText(R.string.check_updates)
            setOnClickListener { checkForUpdates(manual = true) }
        }
        settingsPage.addView(updateBtn, spaced(8))

        val helpPage = page()
        helpPage.addView(label("How to use", 18f, true))
        helpPage.addView(
            label(
                "1. Open Settings and allow \"Display over other apps\".\n\n" +
                        "2. Return to Script and type or paste your speech.\n\n" +
                        "3. Tap Start Teleprompter. The floating panel appears over your screen.\n\n" +
                        "4. Open your camera app. Drag the panel's top handle to move it and tap Play to scroll.\n\n" +
                        "5. Change scrolling speed, font size, or theme from Settings.",
                14f,
                false,
            ),
            spaced(12),
        )

        pages = listOf(scriptPage, settingsPage, helpPage).map { pageContent ->
            ScrollView(this).apply { addView(pageContent) }
        }
        content = FrameLayout(this)
        pages.forEach { content.addView(it, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )) }
        root.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navButtons = listOf(
            button(getString(R.string.nav_script)) { selectPage(0) },
            button(getString(R.string.nav_settings)) { selectPage(1) },
            button(getString(R.string.nav_help)) { selectPage(2) },
        )
        navButtons.forEach { nav.addView(it, flexBtnParams()) }
        root.addView(nav, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        if (savedInstanceState == null) {
            showStartupAnimation(root)
        } else {
            setContentView(root)
        }
        selectPage(currentPage, animate = false)

        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                // Only a clearly horizontal, quick swipe counts.
                if (abs(dx) < dp(80) || abs(dx) < abs(dy) * 1.8f || abs(velocityX) < 700f) return false
                if (startedInEditor(e1)) return false
                if (dx < 0 && currentPage < pages.size - 1) selectPage(currentPage + 1)
                else if (dx > 0 && currentPage > 0) selectPage(currentPage - 1)
                return false
            }
        })

        checkForUpdates(manual = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PAGE, currentPage)
        super.onSaveInstanceState(outState)
    }

    private fun showStartupAnimation(appContent: View) {
        val host = FrameLayout(this)
        host.addView(
            appContent,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val intro = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(27, 33, 35))
            isClickable = true
            isFocusable = true
        }
        val animationView = StartupAnimationView(this)
        intro.addView(
            animationView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        host.addView(
            intro,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(host)

        var dismissed = false
        lateinit var animator: ValueAnimator
        fun dismiss() {
            if (dismissed) return
            dismissed = true
            animator.cancel()
            intro.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction { host.removeView(intro) }
                .start()
        }

        intro.setOnClickListener { dismiss() }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5900
            interpolator = LinearInterpolator()
            addUpdateListener { animationView.progress = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    dismiss()
                }
            })
            start()
        }
    }

    /** Watch every touch for a left/right swipe, without stealing it from the views underneath. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** A swipe that starts inside the script box (while typing) should move the cursor, not the page. */
    private fun startedInEditor(e: MotionEvent): Boolean {
        if (currentPage != 0 || !scriptEdit.hasFocus()) return false
        val r = android.graphics.Rect()
        scriptEdit.getGlobalVisibleRect(r)
        return r.contains(e.rawX.toInt(), e.rawY.toInt())
    }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(16), dp(4), dp(16))
    }

    private fun resetPage(page: View) {
        page.translationX = 0f
        page.alpha = 1f
        page.scaleX = 1f
        page.scaleY = 1f
    }

    /**
     * Shows page [index]. When [animate] is true the new page slides in from the side it lives on
     * (right when going forward, left when going back) while the old page slides out and fades.
     */
    private fun selectPage(index: Int, animate: Boolean = true) {
        val previous = currentPage
        currentPage = index

        navButtons.forEachIndexed { i, button ->
            button.background = GradientDrawable().apply {
                setColor(if (i == index) Color.rgb(46, 204, 154) else Color.TRANSPARENT)
                cornerRadius = dp(12).toFloat()
            }
            button.setTextColor(
                if (i == index) Color.BLACK
                else if (prefs.darkMode) Color.WHITE else Color.BLACK,
            )
        }

        pages.forEach { it.animate().cancel() }

        if (!animate || previous == index) {
            pages.forEachIndexed { i, page ->
                resetPage(page)
                page.visibility = if (i == index) ViewGroup.VISIBLE else ViewGroup.GONE
            }
            return
        }

        val direction = if (index > previous) 1 else -1
        val distance = (if (content.width > 0) content.width else resources.displayMetrics.widthPixels).toFloat()
        val outgoing = pages[previous]
        val incoming = pages[index]

        // Anything not involved in this move stays hidden (covers fast repeated taps).
        pages.forEachIndexed { i, page ->
            if (i != index && i != previous) {
                resetPage(page)
                page.visibility = ViewGroup.GONE
            }
        }

        resetPage(outgoing)
        outgoing.visibility = ViewGroup.VISIBLE
        outgoing.animate()
            .translationX(-direction * distance * 0.4f)
            .alpha(0f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(260)
            .setInterpolator(AccelerateInterpolator(1.2f))
            .withEndAction {
                outgoing.visibility = ViewGroup.GONE
                resetPage(outgoing)
            }
            .start()

        incoming.visibility = ViewGroup.VISIBLE
        incoming.translationX = direction * distance
        incoming.alpha = 0f
        incoming.scaleX = 0.94f
        incoming.scaleY = 0.94f
        incoming.animate()
            .translationX(0f)
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(340)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Tell the running service to re-read the saved script, speed and size. */
    private fun sendReload() {
        if (OverlayService.running) {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_RELOAD))
        }
    }

    /** Send the edited script to the running panel once typing pauses. */
    private fun schedulePush() {
        pendingPush?.let { handler.removeCallbacks(it) }
        val r = Runnable { sendReload() }
        pendingPush = r
        handler.postDelayed(r, 700)
    }

    private fun changeWpm(delta: Int) {
        prefs.wpm = (prefs.wpm + delta).coerceIn(40, 400)
        sendReload()
        updateSettingsUI()
    }

    private fun changeSize(delta: Int) {
        prefs.size = (prefs.size + delta).coerceIn(12, 48)
        sendReload()
        updateSettingsUI()
    }

    private fun updateSettingsUI() {
        wpmValueTv.text = getString(R.string.wpm_value, prefs.wpm)
        sizeValueTv.text = getString(R.string.size_value, prefs.size)
    }

    private fun refresh() {
        val ok = Settings.canDrawOverlays(this)
        permBtn.setText(if (ok) R.string.overlay_granted else R.string.grant_overlay)
        permBtn.isEnabled = !ok
        startBtn.setText(if (OverlayService.running) R.string.stop_teleprompter else R.string.start_teleprompter)
        refreshUpdateButton()
        updateSettingsUI()
    }

    private fun toggleTeleprompter() {
        if (OverlayService.running) {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
            startBtn.setText(R.string.start_teleprompter)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant the overlay permission first.", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.text = scriptEdit.text.toString()
        startForegroundService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START))
        startBtn.setText(R.string.stop_teleprompter)
        Toast.makeText(this, "Teleprompter started — open your camera app.", Toast.LENGTH_LONG).show()
    }

    // ---------------------------------------------------------------- updates
    @Suppress("DEPRECATION")
    private fun currentVersion(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
        } catch (e: Exception) {
            "0"
        }

    private fun refreshUpdateButton() {
        if (downloading) return
        updateBtn.setText(R.string.check_updates)
        updateBtn.isEnabled = true
    }

    private fun checkForUpdates(manual: Boolean) {
        if (!Updater.isConfigured()) {
            if (manual) Toast.makeText(this, R.string.update_not_configured, Toast.LENGTH_LONG).show()
            return
        }
        if (manual) updateBtn.isEnabled = false
        Thread {
            var release: Updater.Release? = null
            var error: String? = null
            try {
                release = Updater.latestRelease()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            runOnUiThread {
                if (!downloading) updateBtn.isEnabled = true
                if (isFinishing || isDestroyed) return@runOnUiThread
                val r = release
                when {
                    r == null -> if (manual) {
                        Toast.makeText(this, getString(R.string.update_check_failed, error ?: ""), Toast.LENGTH_LONG).show()
                    }
                    Updater.isNewer(r.version, currentVersion()) -> showUpdateDialog(r)
                    manual -> Toast.makeText(this, getString(R.string.update_up_to_date, currentVersion()), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showUpdateDialog(release: Updater.Release) {
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage(
                "Version ${release.version} is available (you have ${currentVersion()})." +
                        if (release.notes.isNotBlank()) "\n\n" + release.notes.trim().take(400) else "",
            )
            .setPositiveButton("Update") { _, _ -> startUpdate(release) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startUpdate(release: Updater.Release) {
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.update_allow_installs, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        downloading = true
        updateBtn.isEnabled = false
        updateBtn.text = getString(R.string.update_downloading, 0)
        Thread {
            try {
                Updater.install(this, release) { pct ->
                    runOnUiThread { updateBtn.text = getString(R.string.update_downloading, pct) }
                }
                runOnUiThread {
                    downloading = false
                    updateBtn.setText(R.string.update_installing)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    downloading = false
                    refreshUpdateButton()
                    Toast.makeText(this, getString(R.string.update_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun label(text: String, sizeSp: Float, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setPadding(0, dp(2), 0, dp(2))
        return t
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.setOnClickListener { onClick() }
        return b
    }

    private fun flexBtnParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun spaced(topDp: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        lp.topMargin = dp(topDp)
        return lp
    }
}