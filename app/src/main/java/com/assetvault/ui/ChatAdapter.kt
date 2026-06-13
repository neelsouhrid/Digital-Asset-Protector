package com.assetvault.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.assetvault.data.chat.ChatMessageEntity
import com.assetvault.databinding.ItemChatMessageBinding

class ChatAdapter(private val messages: MutableList<ChatMessageEntity>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.binding.tvRole.text = if (message.role == "user") "You" else "Gemini Assistant"
        holder.binding.tvContent.text = message.content
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: ChatMessageEntity) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}
