package com.inventory.app.mobile.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.inventory.app.mobile.R
import com.inventory.app.mobile.databinding.ActivitySetupBinding
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.Utils
import com.inventory.app.mobile.utils.rest.ApiClient

class SetupActivity : AppCompatActivity() {
    private lateinit var binding : ActivitySetupBinding
    private lateinit var sessionManager : SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val numbers = (1..30).map { it.toString() }
        // Create an ArrayAdapter
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, numbers)
        // Set adapter to the MaterialAutoCompleteTextView
        binding.spinPower.adapter = adapter

        sessionManager = SessionManager(this)
        var serverUrl = sessionManager.getServerUrl()
        var power = sessionManager.getRfidPower()

        binding.etServerUrl.setText(serverUrl)
        binding.spinPower.setSelection(power.toInt())
        binding.etServerUrl.setText(serverUrl)
        binding.spinPower.setSelection(power.toInt()-1)

        binding.btnCancel.setOnClickListener {
            startActivity(Intent(this@SetupActivity, LoginActivity::class.java))
            finish()
        }

        binding.btnSave.setOnClickListener {
            var serverUrl = binding.etServerUrl.text.toString()
            var errorUrl = Utils.getBaseUrlError(serverUrl)
            if (errorUrl != null) {
                binding.etServerUrl.error = errorUrl
                return@setOnClickListener
            }

            var str = binding.spinPower.selectedItem as String
            var power = str.toInt()
            sessionManager.setServerUrl(serverUrl)
            sessionManager.setRfidPower(power)

            ApiClient.setup(applicationContext, serverUrl)
            startActivity(Intent(this@SetupActivity, LoginActivity::class.java))
            finish()
        }
    }
}