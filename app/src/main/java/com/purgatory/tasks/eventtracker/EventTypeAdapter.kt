package com.purgatory.tasks.eventtracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.purgatory.tasks.databinding.ItemEventTypeBinding

class EventTypeAdapter(
    private val onLogClicked: (EventType) -> Unit,
    private val onDetailsClicked: (EventType) -> Unit
) : RecyclerView.Adapter<EventTypeAdapter.EventTypeViewHolder>() {

    private val eventTypes = mutableListOf<EventType>()

    fun submitList(items: List<EventType>) {
        eventTypes.clear()
        eventTypes.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventTypeViewHolder {
        val binding = ItemEventTypeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventTypeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventTypeViewHolder, position: Int) {
        holder.bind(eventTypes[position])
    }

    override fun getItemCount(): Int = eventTypes.size

    inner class EventTypeViewHolder(private val binding: ItemEventTypeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EventType) {
            binding.eventNameText.text = item.name
            binding.logEventButton.setOnClickListener { onLogClicked(item) }
            binding.eventDetailsButton.setOnClickListener { onDetailsClicked(item) }
        }
    }
}
