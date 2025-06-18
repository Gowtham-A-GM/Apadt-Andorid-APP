package com.example.adapt.views

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.adapt.databinding.ActivityCustomQueryBinding
import com.example.adapt.db.ChatDatabase
import com.example.adapt.db.CustomQueryDao
import com.example.adapt.db.CustomQueryModel
import com.example.adapt.viewModel.CustomQueryAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CustomQueryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomQueryBinding
    private lateinit var adapter: CustomQueryAdapter
    private lateinit var dao: CustomQueryDao
    private val queryList = mutableListOf<CustomQueryModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomQueryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dao = ChatDatabase.getDatabase(this).customQueryDao()

        adapter = CustomQueryAdapter(queryList) { position ->
            CoroutineScope(Dispatchers.IO).launch {
                dao.deleteQuery(queryList[position])
                loadQueries()
            }
        }

        binding.recyclerQueries.layoutManager = LinearLayoutManager(this)
        binding.recyclerQueries.adapter = adapter

        loadQueries()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAddQuery.setOnClickListener {
            val keyword = binding.etKeyword.text.toString().trim()
            val response = binding.etResponse.text.toString().trim()

            if (keyword.isNotEmpty() && response.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val exists = dao.isKeywordExists(keyword)
                    if (exists > 0) {
                        runOnUiThread {
                            Toast.makeText(this@CustomQueryActivity, "Query already exists", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val query = CustomQueryModel(keyword = keyword.lowercase(), response = response)
                        dao.insertQuery(query)
                        loadQueries()

                        runOnUiThread {
                            binding.etKeyword.text.clear()
                            binding.etResponse.text.clear()
                            Toast.makeText(this@CustomQueryActivity, "Query saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Fill both fields", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun loadQueries() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = dao.getAllQueries().sortedByDescending { it.id }
            runOnUiThread {
                queryList.clear()
                queryList.addAll(list)
                adapter.notifyDataSetChanged()
            }
        }
    }
}
