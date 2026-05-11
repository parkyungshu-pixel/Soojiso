package com.soojiso.autofill

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.soojiso.autofill.databinding.ActivitySettingsBinding

/**
 * Settings screen. Currently holds theme info, Keep list management
 * (clear), and credits. Intentionally small — the main screen is
 * still the primary surface for editing lists.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClearKeep.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.settings_clear_keep)
                .setPositiveButton(R.string.settings_clear_keep) { _, _ ->
                    ListRepository.saveKeep(this, emptyList())
                    Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}
