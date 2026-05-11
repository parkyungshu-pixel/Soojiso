package com.soojiso.autofill

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.soojiso.autofill.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initial contents
        binding.listEditor.setText(ListRepository.getItems(this).joinToString("\n"))
        binding.keepEditor.setText(ListRepository.getKeep(this).joinToString("\n"))
        refreshCounters()

        binding.listEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refreshCounters() }
        })

        binding.btnSave.setOnClickListener {
            ListRepository.saveText(this, binding.listEditor.text.toString())
            refreshCounters()
            toast(R.string.toast_saved)
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Clear the entire list?")
                .setPositiveButton(R.string.clear) { _, _ ->
                    ListRepository.saveItems(this, emptyList())
                    binding.listEditor.setText("")
                    refreshCounters()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnSaveKeep.setOnClickListener {
            ListRepository.saveKeepText(this, binding.keepEditor.text.toString())
            binding.keepEditor.setText(ListRepository.getKeep(this).joinToString("\n"))
            refreshCounters()
            toast(R.string.toast_saved)
        }

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnEnableOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                toast(R.string.enabled)
            }
        }

        binding.btnFloating.setOnClickListener { toggleFloating() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload from storage on every return — the floating buttons may
        // have removed items from the list or appended to the Keep list
        // while we weren't visible.
        reloadEditors()
        updatePermissionButtons()
        updateFloatingButton()
        refreshCounters()
    }

    private fun reloadEditors() {
        val storedList = ListRepository.getItems(this).joinToString("\n")
        if (storedList != binding.listEditor.text.toString() && !binding.listEditor.hasFocus()) {
            binding.listEditor.setText(storedList)
        }
        val storedKeep = ListRepository.getKeep(this).joinToString("\n")
        if (storedKeep != binding.keepEditor.text.toString() && !binding.keepEditor.hasFocus()) {
            binding.keepEditor.setText(storedKeep)
        }
    }

    private fun refreshCounters() {
        val items = ListRepository.getItems(this)
        binding.counter.text = if (items.isEmpty()) {
            getString(R.string.counter_empty)
        } else {
            getString(R.string.counter_format, items.size, items.first())
        }
        binding.keepCounter.text =
            getString(R.string.keep_counter, ListRepository.getKeep(this).size)
    }

    private fun updatePermissionButtons() {
        val accOn = isAccessibilityServiceEnabled()
        binding.btnEnableAccessibility.text =
            getString(if (accOn) R.string.enabled else R.string.enable)
        binding.btnEnableAccessibility.isEnabled = !accOn

        val overlayOn = Settings.canDrawOverlays(this)
        binding.btnEnableOverlay.text =
            getString(if (overlayOn) R.string.enabled else R.string.enable)
        binding.btnEnableOverlay.isEnabled = !overlayOn

        binding.btnFloating.isEnabled = accOn && overlayOn
    }

    private fun updateFloatingButton() {
        binding.btnFloating.setText(
            if (FloatingButtonService.isRunning(this))
                R.string.stop_floating
            else
                R.string.start_floating
        )
    }

    private fun toggleFloating() {
        if (!Settings.canDrawOverlays(this)) {
            toast(R.string.perm_overlay)
            return
        }
        val intent = Intent(this, FloatingButtonService::class.java)
        if (FloatingButtonService.isRunning(this)) {
            intent.action = FloatingButtonService.ACTION_STOP
            startService(intent)
        } else {
            startForegroundService(intent)
        }
        binding.root.postDelayed({ updateFloatingButton() }, 350)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expected = "$packageName/${AutoFillAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }
}
