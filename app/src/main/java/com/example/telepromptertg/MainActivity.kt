package com.example.telepromptertg

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var scriptEdit: EditText
    private lateinit var wpmValueTv: TextView
    private lateinit var sizeValueTv: TextView
    private lateinit var permBtn: Button
    private lateinit var startBtn: Button
    private lateinit var updateBtn: Button
    private var downloading = false

    private val handler = Handler(Looper.getMainLooper())
    private var pendingPush: Runnable? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(24), dp(20), dp(24))

        col.addView(label("Teleprompter", 26f, true))
        col.addView(label(getString(R.string.version_label, currentVersion()), 12f, false))
        col.addView(label("Read your script while recording videos with your camera app.", 14f, false))

        col.addView(label("Script", 16f, true), spaced(16))

        scriptEdit = EditText(this).apply {
            hint = "Enter or paste your text / script here..."
            setText(prefs.text)
            minLines = 6
            maxLines = 12
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(20, 0, 0, 0))
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
        col.addView(scriptEdit, spaced(6))

        // Speed settings
        col.addView(label("Scroll Speed (WPM)", 16f, true), spaced(16))
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
        col.addView(wpmRow, spaced(6))

        // Font Size settings
        col.addView(label("Font Size", 16f, true), spaced(16))
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
        col.addView(sizeRow, spaced(6))

        // Permissions button
        permBtn = Button(this).apply {
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                )
            }
        }
        col.addView(permBtn, spaced(20))

        // Start/Stop Teleprompter button
        startBtn = Button(this).apply {
            setOnClickListener { toggleTeleprompter() }
        }
        col.addView(startBtn, spaced(12))

        updateBtn = Button(this).apply {
            setText(R.string.check_updates)
            setOnClickListener { checkForUpdates(manual = true) }
        }
        col.addView(updateBtn, spaced(12))

        col.addView(
            label(
                "How to use:\n" +
                        "1. Allow \"Display over other apps\" above.\n" +
                        "2. Type or paste your speech into the script box.\n" +
                        "3. Tap Start Teleprompter.\n" +
                        "4. Open your camera app — the floating script appears over the screen.\n" +
                        "5. Drag the top handle to move the overlay; tap Play to scroll.",
                14f,
                false,
            ),
            spaced(20),
        )

        val sv = ScrollView(this)
        sv.addView(col)
        setContentView(sv)

        checkForUpdates(manual = false)
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