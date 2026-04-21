package com.inventory.app.mobile.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.inventory.app.mobile.R
import com.inventory.app.mobile.databinding.ActivityLoginBinding
import com.inventory.app.mobile.databinding.ContentLoginBinding
import com.inventory.app.mobile.databinding.ContentProgressBinding
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.Params.Companion.DEBUG
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.BaseRequest
import com.inventory.app.mobile.utils.rest.requests.SignInRequest
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.SignInResponse
import okhttp3.Headers
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var bindingProgress : ContentProgressBinding
    private lateinit var bindingContent : ContentLoginBinding

    private lateinit var apiInterface: ApiInterface
    private lateinit var sessionManager : SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        bindingProgress = ContentProgressBinding.bind(binding.contentProgress.root)
        bindingContent = ContentLoginBinding.bind(binding.contentMain.root)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        actionBar?.hide()
        sessionManager = SessionManager(this)

        if (DEBUG) {
            bindingContent.etUsername.setText("arei")
            bindingContent.etPassword.setText("arei")
        }

        bindingContent.btnLogin.setOnClickListener {
            bindingProgress.progress.visibility = View.VISIBLE
            var loginParam = SignInRequest(
                bindingContent.etUsername.text.toString(),
                bindingContent.etPassword.text.toString())
            var request = apiInterface.login(loginParam)

            request.enqueue(object : Callback<SignInResponse?> {
                override fun onResponse(
                    call: Call<SignInResponse?>,
                    response: Response<SignInResponse?>
                ) {
                    bindingProgress.progress.visibility = View.GONE
                    var result = response.body()
                    if (result != null) {
                        if (!result.message.isNullOrEmpty()) {
                            Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_SHORT).show()
                        }
                        if (result.result == BaseResponse.RESULT_OK && !result.data.isNullOrEmpty()) {
                            sessionManager.setSessionId(result.data!!)
                            startActivity(Intent(this@LoginActivity,
                                MainActivity::class.java))
                        }
                    } else {
                        Toast.makeText(this@LoginActivity, "Invalid response", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<SignInResponse?>, t: Throwable) {
                    bindingProgress.progress.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Connection failure! " + t.message, Toast.LENGTH_SHORT).show()

                }
            })
        }

        bindingContent.btnSetup.setOnClickListener {
            startSetupActivity()
        }

        var power = sessionManager.getRfidPower()
        if (power == 0.toByte()) {
            startSetupActivity()
        }

        ApiClient.setup(applicationContext, sessionManager.getServerUrl())

        apiInterface = ApiClient.client.create(ApiInterface::class.java)
    }

    private fun startSetupActivity() {
        startActivity(Intent(this@LoginActivity, SetupActivity::class.java))
        finish()
    }
}