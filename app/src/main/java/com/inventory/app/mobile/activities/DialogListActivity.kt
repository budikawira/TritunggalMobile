package com.inventory.app.mobile.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.inventory.app.mobile.adapters.DialogItemAdapter
import com.inventory.app.mobile.databinding.ActivityDialogListBinding
import com.inventory.app.mobile.models.Select2Item
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.GetLocationsRequest
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.GetLocationsResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DialogListActivity : AppCompatActivity(), DialogItemAdapter.OnItemClick {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_LEVEL = "extra_level"
        const val EXTRA_PARENT_ID = "extra_parent_id"
        const val RESULT_ITEM_ID = "result_item_id"
        const val RESULT_ITEM_TEXT = "result_item_text"
    }

    private lateinit var binding: ActivityDialogListBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var apiInterface: ApiInterface
    private lateinit var adapter: DialogItemAdapter

    private var level: Int = 0
    private var parentId: Long = 0
    private var currentCall: Call<GetLocationsResponse?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDialogListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        level = intent.getIntExtra(EXTRA_LEVEL, 0)
        parentId = intent.getLongExtra(EXTRA_PARENT_ID, 0L)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Select"
        setTitle(title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sessionManager = SessionManager(this)
        ApiClient.setup(applicationContext, sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)

        adapter = DialogItemAdapter(ArrayList(), this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(DividerItemDecoration(this, RecyclerView.VERTICAL))
        binding.recyclerView.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                loadData(s?.toString()?.trim() ?: "")
            }
        })

        loadData("")
    }

    private fun loadData(search: String) {
        showLoading(true)
        currentCall?.cancel()
        currentCall = apiInterface.getLocations(
            "Bearer " + sessionManager.getSessionId(),
            GetLocationsRequest(level = level, parentId = parentId, search = search)
        )
        currentCall!!.enqueue(object : Callback<GetLocationsResponse?> {
            override fun onResponse(call: Call<GetLocationsResponse?>, response: Response<GetLocationsResponse?>) {
                if (call.isCanceled) return
                showLoading(false)
                val body = response.body()
                if (body != null && body.result == BaseResponse.RESULT_OK) {
                    val list = ArrayList<Select2Item>()
                    body.data?.forEach { dt -> list.add(dt) }
                    adapter.data = list
                    adapter.notifyDataSetChanged()
                    binding.textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    Toast.makeText(this@DialogListActivity,
                        body?.message ?: "Failed to load data.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<GetLocationsResponse?>, t: Throwable) {
                if (call.isCanceled) return
                showLoading(false)
                Toast.makeText(this@DialogListActivity,
                    "Connection error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onClick(item: Select2Item) {
        val result = Intent().apply {
            putExtra(RESULT_ITEM_ID, item.value)
            putExtra(RESULT_ITEM_TEXT, item.text)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_CANCELED)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
}
