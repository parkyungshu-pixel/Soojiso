package com.soojiso.autofill

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.soojiso.autofill.databinding.ActivitySettingsBinding

/**
 * Settings screen.
 *
 * Currently home to:
 *  - Maximum-pins configuration (1..30)
 *  - Theme info (dark only, Elusive Samurai palette)
 *  - Credits (Yami as author; Tokiyuki as the design inspiration)
 *
 * Kept intentionally small — every control here also exists as a
 * quick knob on the main screen, so Settings is mostly a home for
 * "infrequent" or "informational" items.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.maxPinsInput.setText(ListRepository.getMaxPins(this).toString())

        binding.btnApplyPins.setOnClickListener {
            val raw = binding.maxPinsInput.text.toString().trim()
            val n = raw.toIntOrNull()?.coerceIn(1, 30)
                ?: ListRepository.DEFAULT_MAX_PINS
            ListRepository.setMaxPins(this, n)
            binding.maxPinsInput.setText(n.toString())
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}
