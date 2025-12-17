package chat.sphinx.video_screen.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import chat.sphinx.resources.R
import chat.sphinx.resources.databinding.LayoutChapterListItemHolderBinding
import chat.sphinx.wrapper_common.ChapterProperties

class EpisodeChapterListAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val onChapterClick: (String?) -> Unit
) : RecyclerView.Adapter<EpisodeChapterListAdapter.EpisodeChapterViewHolder>(), DefaultLifecycleObserver {

    private val chapters = mutableListOf<ChapterProperties>()

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    fun submitList(list: List<ChapterProperties>) {
        chapters.clear()
        chapters.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeChapterViewHolder {
        val binding = LayoutChapterListItemHolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EpisodeChapterViewHolder(binding, onChapterClick)
    }

    override fun onBindViewHolder(holder: EpisodeChapterViewHolder, position: Int) {
        holder.bind(chapters[position])
    }

    override fun getItemCount(): Int = chapters.size

    class EpisodeChapterViewHolder(
        private val binding: LayoutChapterListItemHolderBinding,
        private val onChapterClick: (String?) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chapter: ChapterProperties) {
            binding.textViewEpisodeTitle.text = chapter.name
            binding.textViewEpisodeTime.text = chapter.timestamp

            val textColor = if (chapter.isAdBoolean) {
                ContextCompat.getColor(binding.root.context, R.color.secondaryText)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.text)
            }

            binding.textViewEpisodeTitle.setTextColor(textColor)

            binding.root.setOnClickListener {
                onChapterClick(chapter.timestamp)
            }
        }
    }
}