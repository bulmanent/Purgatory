package com.purgatory.tasks.eventtracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.purgatory.tasks.R
import com.purgatory.tasks.databinding.ItemEventLogBinding

class EventLogAdapter : RecyclerView.Adapter<EventLogAdapter.EventLogViewHolder>() {

    private val entries = mutableListOf<EventEntry>()

    fun submitList(items: List<EventEntry>) {
        entries.clear()
        entries.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventLogViewHolder {
        val binding = ItemEventLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventLogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventLogViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount(): Int = entries.size

    inner class EventLogViewHolder(private val binding: ItemEventLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EventEntry) {
            binding.logDateTimeText.text = binding.root.context.getString(
                R.string.event_log_date_time,
                item.date,
                item.time
            )
            binding.logDetailsText.text = item.details.ifBlank {
                binding.root.context.getString(R.string.event_log_empty)
            }
            binding.logSeverityText.text = item.severity.ifBlank {
                binding.root.context.getString(R.string.event_log_empty)
            }
            binding.logActionText.text = item.action.ifBlank {
                binding.root.context.getString(R.string.event_log_empty)
            }
        }
    }
}
