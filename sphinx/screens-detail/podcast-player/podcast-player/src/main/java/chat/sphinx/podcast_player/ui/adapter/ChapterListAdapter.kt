package chat.sphinx.podcast_player.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import chat.sphinx.resources.databinding.LayoutChapterListItemHolderBinding
import chat.sphinx.wrapper_podcast.ChapterProperties

class ChapterListAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val onChapterClick: (Long) -> Unit
) : RecyclerView.Adapter<ChapterListAdapter.ChapterViewHolder>(), DefaultLifecycleObserver {

    private val chapters = mutableListOf<ChapterProperties>()

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    fun submitList(list: List<ChapterProperties>) {
        val filteredList = list.filter {
            !it.name.isNullOrBlank() && !it.timestamp.isNullOrBlank()
        }
        chapters.clear()
        chapters.addAll(filteredList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val binding = LayoutChapterListItemHolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChapterViewHolder(binding, onChapterClick)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        holder.bind(chapters[position])
    }

    override fun getItemCount(): Int = chapters.size

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
    }

    class ChapterViewHolder(
        private val binding: LayoutChapterListItemHolderBinding,
        private val onChapterClick: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chapter: ChapterProperties) {
            binding.textViewEpisodeTitle.text = chapter.name
            binding.textViewEpisodeTime.text = chapter.timestamp

            binding.root.setOnClickListener {
                val millis = parseTimestampToMillis(chapter.timestamp)
                onChapterClick(millis)
            }
        }

        private fun parseTimestampToMillis(timestamp: String?): Long {
            if (timestamp.isNullOrBlank()) return 0L
            val parts = timestamp.split(":")
            if (parts.size != 3) return 0L
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val seconds = parts[2].toLongOrNull() ?: 0L
            return (hours * 3600 + minutes * 60 + seconds) * 1000L
        }
    }
}
