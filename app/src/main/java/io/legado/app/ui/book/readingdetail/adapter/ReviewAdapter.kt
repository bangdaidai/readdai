package io.legado.app.ui.book.readingdetail.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.BookReview
import io.legado.app.databinding.ItemReviewBinding

class ReviewAdapter : ListAdapter<BookReview, ReviewAdapter.ViewHolder>(DiffCallback) {
    
    private var onItemClickListener: ((BookReview, Int) -> Unit)? = null
    
    fun setOnItemClickListener(listener: (BookReview, Int) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(private val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(review: BookReview, position: Int) {
            binding.tvReviewContent.text = review.reviewContent
            
            // 设置点击事件
            binding.root.setOnClickListener {
                onItemClickListener?.invoke(review, position)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<BookReview>() {
        override fun areItemsTheSame(oldItem: BookReview, newItem: BookReview): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BookReview, newItem: BookReview): Boolean {
            return oldItem == newItem
        }
    }
}