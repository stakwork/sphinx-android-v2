package chat.sphinx.dashboard.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import chat.sphinx.concept_repository_dashboard.model.HiveTask
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.ItemHiveTaskBinding
import chat.sphinx.dashboard.ui.HiveTaskAction
import chat.sphinx.dashboard.ui.HiveTaskActions

internal class HiveTaskAdapter(
    private val onStartClicked: (HiveTask) -> Unit,
    private val onRetryClicked: (HiveTask) -> Unit,
    private val onMarkCompleteClicked: (HiveTask) -> Unit,
    private val onArchiveClicked: (HiveTask) -> Unit,
    private val onUnarchiveClicked: (HiveTask) -> Unit,
    private val onDuplicateClicked: (HiveTask) -> Unit,
    private val onFlagsChanged: (HiveTask, Boolean, Boolean, Boolean) -> Unit,
) : ListAdapter<HiveTask, HiveTaskAdapter.HiveTaskViewHolder>(DIFF_CALLBACK) {

    var archivedSegment: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HiveTask>() {
            override fun areItemsTheSame(oldItem: HiveTask, newItem: HiveTask): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: HiveTask, newItem: HiveTask): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HiveTaskViewHolder {
        val binding = ItemHiveTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HiveTaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HiveTaskViewHolder, position: Int) {
        holder.bind(getItem(position), archivedSegment)
    }

    inner class HiveTaskViewHolder(
        private val binding: ItemHiveTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: HiveTask, archivedSegment: Boolean) {
            binding.apply {
                textViewHiveTaskTitle.text = task.title
                textViewHiveTaskStatus.text = root.context.getString(
                    R.string.hive_task_status_label,
                    task.status.orEmpty()
                )
                textViewHiveTaskPriority.text = root.context.getString(
                    R.string.hive_task_priority_label,
                    task.priority.orEmpty()
                )
                textViewHiveTaskFailed.visibility =
                    if (HiveTaskActions.isFailed(task)) View.VISIBLE else View.GONE

                val available = HiveTaskActions.available(task, archivedSegment)
                textViewHiveTaskOverflow.setOnClickListener { anchor ->
                    showOverflowMenu(anchor, task, available)
                }
            }
        }

        private fun showOverflowMenu(
            anchor: View,
            task: HiveTask,
            available: Set<HiveTaskAction>,
        ) {
            PopupMenu(anchor.context, anchor).apply {
                inflate(R.menu.menu_hive_task)

                menu.findItem(R.id.menu_hive_task_start).isVisible =
                    HiveTaskAction.START in available
                menu.findItem(R.id.menu_hive_task_retry).isVisible =
                    HiveTaskAction.RETRY in available
                menu.findItem(R.id.menu_hive_task_mark_complete).isVisible =
                    HiveTaskAction.MARK_COMPLETE in available
                menu.findItem(R.id.menu_hive_task_archive).isVisible =
                    HiveTaskAction.ARCHIVE in available
                menu.findItem(R.id.menu_hive_task_unarchive).isVisible =
                    HiveTaskAction.UNARCHIVE in available
                menu.findItem(R.id.menu_hive_task_duplicate).isVisible =
                    HiveTaskAction.DUPLICATE in available

                val canEditFlags = HiveTaskAction.EDIT_FLAGS in available
                val autoMergeItem = menu.findItem(R.id.menu_hive_task_auto_merge)
                val runBuildItem = menu.findItem(R.id.menu_hive_task_run_build)
                val runTestSuiteItem = menu.findItem(R.id.menu_hive_task_run_test_suite)
                autoMergeItem.isVisible = canEditFlags
                runBuildItem.isVisible = canEditFlags
                runTestSuiteItem.isVisible = canEditFlags
                autoMergeItem.isEnabled = canEditFlags
                runBuildItem.isEnabled = canEditFlags
                runTestSuiteItem.isEnabled = canEditFlags
                autoMergeItem.isChecked = task.autoMerge == true
                runBuildItem.isChecked = task.runBuild == true
                runTestSuiteItem.isChecked = task.runTestSuite == true

                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_hive_task_start -> {
                            onStartClicked(task)
                            true
                        }
                        R.id.menu_hive_task_retry -> {
                            onRetryClicked(task)
                            true
                        }
                        R.id.menu_hive_task_mark_complete -> {
                            onMarkCompleteClicked(task)
                            true
                        }
                        R.id.menu_hive_task_archive -> {
                            onArchiveClicked(task)
                            true
                        }
                        R.id.menu_hive_task_unarchive -> {
                            onUnarchiveClicked(task)
                            true
                        }
                        R.id.menu_hive_task_duplicate -> {
                            onDuplicateClicked(task)
                            true
                        }
                        R.id.menu_hive_task_auto_merge -> {
                            onFlagsChanged(
                                task,
                                !(task.autoMerge == true),
                                task.runBuild == true,
                                task.runTestSuite == true,
                            )
                            true
                        }
                        R.id.menu_hive_task_run_build -> {
                            onFlagsChanged(
                                task,
                                task.autoMerge == true,
                                !(task.runBuild == true),
                                task.runTestSuite == true,
                            )
                            true
                        }
                        R.id.menu_hive_task_run_test_suite -> {
                            onFlagsChanged(
                                task,
                                task.autoMerge == true,
                                task.runBuild == true,
                                !(task.runTestSuite == true),
                            )
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
