package chat.sphinx.dashboard.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import chat.sphinx.concept_repository_dashboard.model.HivePod
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.ItemHivePodBinding
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.visible

internal class HivePodAdapter : ListAdapter<HivePod, HivePodAdapter.HivePodViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HivePod>() {
            override fun areItemsTheSame(oldItem: HivePod, newItem: HivePod): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: HivePod, newItem: HivePod): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HivePodViewHolder {
        val binding = ItemHivePodBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HivePodViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HivePodViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HivePodViewHolder(
        private val binding: ItemHivePodBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pod: HivePod) {
            binding.apply {
                textViewHivePodSubdomain.text = pod.subdomain
                textViewHivePodStatus.text = formatStatus(pod)

                val usage = pod.resourceUsage
                if (usage?.available == true) {
                    textViewHivePodCapacity.visible
                    textViewHivePodCapacity.text = root.context.getString(
                        R.string.hive_pod_capacity_label,
                        usage.requestsCpu.orEmpty(),
                        usage.requestsMemory.orEmpty(),
                        usage.usageCpu.orEmpty(),
                        usage.usageMemory.orEmpty(),
                    )
                } else {
                    textViewHivePodCapacity.gone
                    textViewHivePodCapacity.text = ""
                }
            }
        }

        private fun formatStatus(pod: HivePod): String {
            val usage = pod.usageStatus
            return if (usage.isNullOrBlank()) {
                pod.state
            } else {
                "${pod.state} · $usage"
            }
        }
    }
}
