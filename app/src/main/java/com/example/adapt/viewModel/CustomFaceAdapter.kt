package com.example.adapt.viewModel

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.adapt.R
import com.example.adapt.db.RegisteredFaceModel

class CustomFaceAdapter(
    private val faceList: MutableList<RegisteredFaceModel>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<CustomFaceAdapter.FaceViewHolder>() {

    inner class FaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val faceImage: ImageView = itemView.findViewById(R.id.ivFace)
        val name: TextView = itemView.findViewById(R.id.tvName)
        val response: TextView = itemView.findViewById(R.id.tvResponse)
        val deleteBtn: ImageButton = itemView.findViewById(R.id.btnDeleteFace)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_custom_face, parent, false)
        return FaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: FaceViewHolder, position: Int) {
        val face = faceList[position]
        holder.name.text = face.name
        holder.response.text = face.response

        face.image?.let {
            val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            holder.faceImage.setImageBitmap(bitmap)
        } ?: holder.faceImage.setImageResource(R.drawable.custom_face_img_placeholder)

        holder.deleteBtn.setOnClickListener {
            onDeleteClick(position)
        }
    }

    override fun getItemCount(): Int = faceList.size
}
