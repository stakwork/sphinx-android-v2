package chat.sphinx.dashboard.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import chat.sphinx.concept_repository_dashboard.model.HiveFeature
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.ItemHiveFeatureBinding

internal class HiveFeatureAdapter(
    private val onFeatureClicked: (HiveFeature) -> Unit,
    private val onEditClicked: (HiveFeature) -> Unit,
    private val onDeleteClicked: (HiveFeature) -> Unit,
) : ListAdapter<HiveFeature, HiveFeatureAdapter.HiveFeatureViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HiveFeature>() {
            override fun areItemsTheSame(oldItem: HiveFeature, newItem: HiveFeature): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: HiveFeature, newItem: HiveFeature): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HiveFeatureViewHolder {
        val binding = ItemHiveFeatureBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HiveFeatureViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HiveFeatureViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HiveFeatureViewHolder(
        private val binding: ItemHiveFeatureBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(feature: HiveFeature) {
            binding.apply {
                textViewHiveFeatureTitle.text = feature.title
                textViewHiveFeatureStatus.text = root.context.getString(
                    R.string.hive_feature_status_label,
                    feature.status.orEmpty()
                )
                textViewHiveFeaturePriority.text = root.context.getString(
                    R.string.hive_feature_priority_label,
                    feature.priority.orEmpty()
                )

                root.setOnClickListener { onFeatureClicked(feature) }
                textViewHiveFeatureOverflow.setOnClickListener { anchor ->
                    PopupMenu(anchor.context, anchor).apply {
                        inflate(R.menu.menu_hive_feature)
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.menu_hive_feature_edit -> {
                                    onEditClicked(feature)
                                    true
                                }
                                R.id.menu_hive_feature_delete -> {
                                    onDeleteClicked(feature)
                                    true
                                }
                                else -> false
                            }
                        }
                        show()
                    }
                }
            }
        }
    }
}
