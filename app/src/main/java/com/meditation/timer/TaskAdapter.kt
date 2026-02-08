package com.meditation.timer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.meditation.timer.databinding.ItemTaskBinding

class TaskAdapter(
    private val onActionClicked: (Task) -> Unit,
    private val onItemClicked: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val tasks = mutableListOf<Task>()

    fun submitList(items: List<Task>) {
        tasks.clear()
        tasks.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount(): Int = tasks.size

    inner class TaskViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.taskDetails.text = task.details

            val ownerName = task.owner?.displayName ?: "-"
            val dueText = when {
                task.status == TaskStatus.UNASSIGNED -> binding.root.context.getString(R.string.task_unassigned)
                task.dueDate != null -> DateUtils.format(task.dueDate)
                else -> "-"
            }
            binding.taskMeta.text =
                "${binding.root.context.getString(R.string.owner_label)}: $ownerName · " +
                    "${binding.root.context.getString(R.string.importance_label)}: ${task.importance} · " +
                    "${binding.root.context.getString(R.string.task_due_label)}: $dueText"

            val statusLabel = when (task.status) {
                TaskStatus.UNASSIGNED -> binding.root.context.getString(R.string.task_unassigned)
                TaskStatus.DUE -> binding.root.context.getString(R.string.task_due)
                TaskStatus.CRUCIAL -> binding.root.context.getString(R.string.task_crucial)
                TaskStatus.COMPLETE -> binding.root.context.getString(R.string.task_complete)
            }
            binding.taskStatus.text = statusLabel
            val statusColor = when (task.status) {
                TaskStatus.CRUCIAL -> R.color.status_crucial
                TaskStatus.DUE -> R.color.status_due
                TaskStatus.COMPLETE -> R.color.status_complete
                TaskStatus.UNASSIGNED -> R.color.ink_600
            }
            binding.taskStatus.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, statusColor)
            )

            val ownerColor = task.owner?.colorRes ?: R.color.ink_600
            binding.ownerStripe.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, ownerColor)
            )
            binding.taskCard.strokeColor = ContextCompat.getColor(binding.root.context, ownerColor)

            if (task.status == TaskStatus.COMPLETE) {
                binding.taskActionButton.text = binding.root.context.getString(R.string.task_restore)
            } else {
                binding.taskActionButton.text = binding.root.context.getString(R.string.task_mark_complete)
            }
            binding.taskActionButton.setOnClickListener { onActionClicked(task) }
            binding.root.setOnClickListener { onItemClicked(task) }
            binding.taskActionButton.visibility =
                if (task.status == TaskStatus.UNASSIGNED || task.status == TaskStatus.DUE || task.status == TaskStatus.CRUCIAL || task.status == TaskStatus.COMPLETE) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }
    }
}
