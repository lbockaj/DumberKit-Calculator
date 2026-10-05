package org.fossify.math.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.math.databinding.ItemHistoryBinding
import org.fossify.math.helpers.ExpressionDisplay
import org.fossify.math.helpers.dpadHighlight
import org.fossify.math.models.History

/** The calculations, oldest first; the one picked with ↑/↓ is framed (the list itself never takes the focus). */
class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
    private var items: List<History> = emptyList()

    var selectedPosition = RecyclerView.NO_POSITION
        set(value) {
            val old = field
            field = value
            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
            if (value != RecyclerView.NO_POSITION) notifyItemChanged(value)
        }

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(newItems: List<History>) {
        items = newItems
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    fun getItem(position: Int) = items.getOrNull(position)

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            val context = root.context
            val textColor = context.getProperTextColor()
            val primary = context.getProperPrimaryColor()
            historyFormula.setTextColor(textColor)
            historyResult.setTextColor(textColor)
            historyFormula.text = ExpressionDisplay.format(item.formula, primary)
            historyResult.text = "= ${ExpressionDisplay.format(item.result)}"
            root.foreground = if (position == selectedPosition) context.dpadHighlight() else null
        }
    }

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)
}
