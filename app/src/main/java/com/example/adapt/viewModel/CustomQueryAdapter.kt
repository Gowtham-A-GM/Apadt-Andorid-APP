package com.example.adapt.viewModel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.adapt.R
import com.example.adapt.db.CustomQueryModel

class CustomQueryAdapter(
    private val queryList: MutableList<CustomQueryModel>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<CustomQueryAdapter.QueryViewHolder>() {

    inner class QueryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val keyword: TextView = itemView.findViewById(R.id.tvKeyword)
        val response: TextView = itemView.findViewById(R.id.tvResponse)
        val deleteBtn: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_custom_query, parent, false)
        return QueryViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueryViewHolder, position: Int) {
        val query = queryList[position]
        holder.keyword.text = query.keyword
        holder.response.text = query.response
        holder.deleteBtn.setOnClickListener {
            onDeleteClick(position)
        }
    }

    override fun getItemCount(): Int = queryList.size
}
