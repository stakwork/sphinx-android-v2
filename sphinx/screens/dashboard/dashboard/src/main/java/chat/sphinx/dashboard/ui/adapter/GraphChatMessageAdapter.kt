package chat.sphinx.dashboard.ui.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.ItemGraphChatMessageBinding
import chat.sphinx.dashboard.graphchat.GraphChatMessage
import chat.sphinx.dashboard.ui.GraphChatUiMessage

internal class GraphChatMessageAdapter :
    ListAdapter<GraphChatUiMessage, GraphChatMessageAdapter.MessageViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<GraphChatUiMessage>() {
            override fun areItemsTheSame(
                oldItem: GraphChatUiMessage,
                newItem: GraphChatUiMessage,
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: GraphChatUiMessage,
                newItem: GraphChatUiMessage,
            ): Boolean = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemGraphChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(
        private val binding: ItemGraphChatMessageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: GraphChatUiMessage) {
            val isUser = message.role == GraphChatMessage.ROLE_USER
            binding.textViewGraphChatMessage.text = message.content
            binding.textViewGraphChatMessage.setBackgroundResource(
                if (isUser) {
                    R.drawable.bg_graph_chat_bubble_user
                } else {
                    R.drawable.bg_graph_chat_bubble_assistant
                }
            )
            val params = binding.textViewGraphChatMessage.layoutParams as FrameLayout.LayoutParams
            params.gravity = if (isUser) Gravity.END else Gravity.START
            binding.textViewGraphChatMessage.layoutParams = params
        }
    }
}
