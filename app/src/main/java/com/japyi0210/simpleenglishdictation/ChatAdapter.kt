package com.japyi0210.simpleenglishdictation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.japyi0210.simpleenglishdictation.R
import com.japyi0210.simpleenglishdictation.model.ChatMessage

/**
 * ChatGPT 질문/응답을 표시하는 RecyclerView 어댑터
 */
class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    /**
     * 사용자 또는 GPT의 메시지를 위한 뷰 홀더
     */
    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageTextView: TextView = view.findViewById(R.id.chatTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layoutId = if (viewType == 0) {
            R.layout.item_chat_user // 사용자 메시지 레이아웃
        } else {
            R.layout.item_chat_gpt // GPT 메시지 레이아웃
        }

        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.messageTextView.text = messages[position].message
    }

    override fun getItemCount(): Int = messages.size

    /**
     * 메시지의 발신자에 따라 뷰타입 결정 (0: 사용자, 1: GPT)
     */
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) 0 else 1
    }
}
