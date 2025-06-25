package market.symbol.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.claw.ai.R

class TimeframeDropdownAdapter(
    private var items: List<String>,
    private val onTimeframeSelected: (String) -> Unit
) : RecyclerView.Adapter<TimeframeDropdownAdapter.TimeframeViewHolder>() {

    companion object {
        const val CUSTOM_ITEM = "Add Custom..."
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeframeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.custom_dropdown_item, parent, false)
        return TimeframeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimeframeViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<String>) {
        // Ensure "Add Custom..." is always the last item
        val sortedItems = newItems.filter { it != CUSTOM_ITEM }.toMutableList()
        sortedItems.add(CUSTOM_ITEM)
        this.items = sortedItems
        notifyDataSetChanged()
    }

    inner class TimeframeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.timeframe_text)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onTimeframeSelected(items[adapterPosition])
                }
            }
        }

        fun bind(text: String) {
            textView.text = text
            // Style the "Add Custom..." item differently
            if (text == CUSTOM_ITEM) {
                textView.setTextColor(Color.parseColor("#82aaff")) // A distinct color for the custom option
            } else {
                textView.setTextColor(Color.WHITE)
            }
        }
    }
}