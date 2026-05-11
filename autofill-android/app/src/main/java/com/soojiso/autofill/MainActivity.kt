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

        // Prefill the list editor
        binding.listEditor.setText(ListRepository.getItems(this).joinToString("\n"))
        refreshCounter()

        // Prefill the pins editor + max-pins input
        binding.pinsEditor.setText(ListRepository.getPins(this).joinToString("\n"))
        binding.maxPinsInput.setText(ListRepository.getMaxPins(this).toString())
        refreshPinsDesc()

        binding.listEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refreshCounter() }
        })

        binding.btnSave.setOnClickListener {
            ListRepository.saveText(this, binding.listEditor.text.toString())
            refreshCounter()
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Clear the entire list?")
                .setPositiveButton(R.string.clear) { _, _ ->
                    ListRepository.saveItems(this, emptyList())
                    binding.listEditor.setText("")
                    refreshCounter()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnSavePins.setOnClickListener {
            val maxRaw = binding.maxPinsInput.text.toString().trim()
            val max = maxRaw.toIntOrNull()?.coerceIn(1, 20)
                ?: ListRepository.DEFAULT_MAX_PINS
            ListRepository.setMaxPins(this, max)
            binding.maxPinsInput.setText(max.toString())

            ListRepository.savePinsText(this, binding.pinsEditor.text.toString())

            // Reflect trimming back into the editor (in case user went over the cap)
            binding.pinsEditor.setText(ListRepository.getPins(this).joinToString("\n"))
            refreshPinsDesc()
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, R.string.enabled, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnFloating.setOnClickListener { toggleFloating() }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
        updateFloatingButton()
        refreshCounter()
        refreshPinsDesc()
    }

    private fun refreshCounter() {
        val items = ListRepository.getItems(this)
        binding.counter.text = if (items.isEmpty()) {
            getString(R.string.counter_empty)
        } else {
            getString(R.string.counter_format, items.size, items.first())
        }
    }

    private fun refreshPinsDesc() {
        binding.pinsDesc.text = getString(
            R.string.pins_desc,
            ListRepository.getMaxPins(this)
        )
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
            Toast.makeText(this, R.string.perm_overlay, Toast.LENGTH_SHORT).show()
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
}
