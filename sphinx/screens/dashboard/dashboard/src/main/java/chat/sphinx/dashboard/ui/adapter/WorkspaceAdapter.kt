package chat.sphinx.dashboard.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import chat.sphinx.concept_image_loader.Disposable
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_image_loader.ImageLoaderOptions
import chat.sphinx.concept_repository_dashboard.model.Workspace
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.ItemWorkspaceBinding
import io.matthewnelson.android_feature_viewmodel.util.OnStopSupervisor
import kotlinx.coroutines.launch

internal class WorkspaceAdapter(
    private val imageLoader: ImageLoader<ImageView>,
    private val onStopSupervisor: OnStopSupervisor,
) : ListAdapter<Workspace, WorkspaceAdapter.WorkspaceViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Workspace>() {
            override fun areItemsTheSame(oldItem: Workspace, newItem: Workspace): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Workspace, newItem: Workspace): Boolean =
                oldItem == newItem
        }
    }

    private val imageLoaderOptions: ImageLoaderOptions = ImageLoaderOptions.Builder()
        .placeholderResId(R.drawable.ic_workspace_placeholder)
        .build()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkspaceViewHolder {
        val binding = ItemWorkspaceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WorkspaceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorkspaceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: WorkspaceViewHolder) {
        super.onViewRecycled(holder)
        holder.onRecycled()
    }

    inner class WorkspaceViewHolder(
        private val binding: ItemWorkspaceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        var disposable: Disposable? = null

        fun bind(workspace: Workspace) {
            binding.apply {
                textViewWorkspaceName.text = workspace.name
                textViewWorkspaceRole.text = workspace.userRole ?: "Member"
                textViewWorkspaceMembers.text = "Members: ${workspace.memberCount}"

                disposable?.dispose()
                disposable = null

                val logoUrl = workspace.logoUrl
                if (logoUrl != null) {
                    onStopSupervisor.scope.launch {
                        imageLoader.load(
                            imageViewWorkspaceLogo,
                            logoUrl,
                            imageLoaderOptions
                        ).also { disposable = it }
                    }
                } else {
                    imageViewWorkspaceLogo.setImageResource(R.drawable.ic_workspace_placeholder)
                }
            }
        }

        fun onRecycled() {
            disposable?.dispose()
            disposable = null
        }
    }
}
