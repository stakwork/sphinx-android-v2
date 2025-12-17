package chat.sphinx.podcast_player.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import chat.sphinx.resources.databinding.LayoutChapterListItemHolderBinding
import chat.sphinx.wrapper_common.ChapterProperties

class ChapterListAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val onChapterClick: (String?) -> Unit
) : RecyclerView.Adapter<ChapterListAdapter.ChapterViewHolder>(), DefaultLifecycleObserver {

    private val chapters = mutableListOf<ChapterProperties>()

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    fun submitList(list: List<ChapterProperties>) {
        chapters.clear()
        chapters.addAll(list)
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
        private val onChapterClick: (String?) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chapter: ChapterProperties) {
            binding.textViewEpisodeTitle.text = chapter.name
            binding.textViewEpisodeTime.text = chapter.timestamp

            binding.root.setOnClickListener {
                onChapterClick(chapter.timestamp)
            }
        }
    }
}
