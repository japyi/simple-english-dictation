package com.japyi0210.simpleenglishdictation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ReviewAdapter(
    private val reviewList: List<Review>
) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sentenceText: TextView = view.findViewById(R.id.sentenceText)
        val inputText: TextView = view.findViewById(R.id.inputText)
        val feedbackText: TextView = view.findViewById(R.id.feedbackText)
        val dateText: TextView = view.findViewById(R.id.dateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review_card, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = reviewList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val review = reviewList[position]

        holder.sentenceText.text = "정답: ${review.sentence}"

        // ✅ 여기를 수정하여 듣기 횟수와 일치율 포함
        holder.inputText.text = "입력: ${review.userInput} (${review.replayCount}회 듣기, ${review.similarity}% 일치)"

        holder.feedbackText.text = review.feedback

        val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
        val formattedDate = review.timestamp?.let { dateFormat.format(it) } ?: ""
        holder.dateText.text = formattedDate

        val animation = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.slide_up)
        holder.itemView.startAnimation(animation)
    }
}
