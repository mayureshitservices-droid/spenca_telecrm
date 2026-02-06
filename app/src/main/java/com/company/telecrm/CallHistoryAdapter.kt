package com.company.telecrm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class CallHistoryItem {
    data class Header(val date: String) : CallHistoryItem()
    data class Call(val callHistory: CallHistory) : CallHistoryItem()
}

class CallHistoryAdapter(
    private val context: Context,
    private var items: MutableList<CallHistoryItem>,
    private val onActionClick: (CallHistory) -> Unit,
    private val onWhatsAppClick: (CallHistory) -> Unit,
    private val phoneNameRepository: PhoneNameRepository = PhoneNameRepository(context)
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPosition: Int? = null
    private var expandedPosition: Int? = null

    private val TYPE_HEADER = 0
    private val TYPE_CALL = 1

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.date_header_text)
    }

    inner class CallViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val customerNameLabel: TextView = view.findViewById(R.id.customer_name_label)
        val phoneNumber: TextView = view.findViewById(R.id.phone_number)
        val callDuration: TextView = view.findViewById(R.id.call_duration)
        val timestamp: TextView = view.findViewById(R.id.timestamp)
        val playRecording: Button = view.findViewById(R.id.play_recording)
        val callStatusChip: TextView = view.findViewById(R.id.call_status_chip)
        val outcomeStatusChip: TextView = view.findViewById(R.id.outcome_status_chip)
        val btnLogAction: ImageButton = view.findViewById(R.id.btn_log_action)
        val btnSendWhatsApp: ImageButton = view.findViewById(R.id.btn_send_whatsapp)
        val detailContainer: LinearLayout = view.findViewById(R.id.detail_container)
        val detailCustomerName: TextView = view.findViewById(R.id.detail_customer_name)
        val orderTable: android.widget.TableLayout = view.findViewById(R.id.order_table)
        val detailRemarks: TextView = view.findViewById(R.id.detail_remarks)

        init {
            playRecording.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlay(position)
                }
            }
            // Listeners are now moved to onBindViewHolder for better reliability with dynamic visibility
            btnSendWhatsApp.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = items[position]
                    if (item is CallHistoryItem.Call) {
                        onWhatsAppClick(item.callHistory)
                    }
                }
            }
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val prevExpanded = expandedPosition
                    expandedPosition = if (expandedPosition == position) null else position
                    
                    prevExpanded?.let { notifyItemChanged(it) }
                    expandedPosition?.let { notifyItemChanged(it) }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is CallHistoryItem.Header) TYPE_HEADER else TYPE_CALL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.date_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.call_history_item, parent, false)
            CallViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is CallHistoryItem.Header) {
            holder.dateText.text = item.date
        } else if (holder is CallViewHolder && item is CallHistoryItem.Call) {
            val call = item.callHistory
            
            // Handle Global Customer Name
            val savedName = phoneNameRepository.getName(call.phoneNumber)
            if (!savedName.isNullOrEmpty()) {
                holder.customerNameLabel.text = savedName
                holder.customerNameLabel.visibility = View.VISIBLE
            } else if (!call.customerName.isNullOrEmpty()) {
                holder.customerNameLabel.text = call.customerName
                holder.customerNameLabel.visibility = View.VISIBLE
            } else {
                holder.customerNameLabel.visibility = View.GONE
            }

            holder.phoneNumber.text = call.phoneNumber
            holder.callDuration.text = call.callDuration
            holder.timestamp.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(call.timestamp))
            
            // Primary status chip
            holder.callStatusChip.text = call.status
            if (call.status == "Answered") {
                holder.callStatusChip.setBackgroundResource(R.drawable.bg_status_chip)
                holder.callStatusChip.backgroundTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.status_ordered_bg))
                holder.callStatusChip.setTextColor(context.getColor(R.color.status_ordered_text))
            } else {
                holder.callStatusChip.setBackgroundResource(R.drawable.bg_status_chip)
                holder.callStatusChip.backgroundTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.status_lost_bg))
                holder.callStatusChip.setTextColor(context.getColor(R.color.status_lost_text))
            }

            // Outcome status chip
            if (call.outcome != null && call.outcome != "Select Outcome") {
                holder.outcomeStatusChip.visibility = View.VISIBLE
                holder.outcomeStatusChip.text = call.outcome
                val (bgColor, textColor) = when (call.outcome) {
                    "Ordered" -> R.color.status_ordered_bg to R.color.status_ordered_text
                    "Lost" -> R.color.status_lost_bg to R.color.status_lost_text
                    else -> R.color.status_pending_bg to R.color.status_pending_text
                }
                holder.outcomeStatusChip.backgroundTintList = android.content.res.ColorStateList.valueOf(context.getColor(bgColor))
                holder.outcomeStatusChip.setTextColor(context.getColor(textColor))
            } else {
                holder.outcomeStatusChip.visibility = View.GONE
            }

            // Hide log action if already ordered
            holder.btnLogAction.visibility = if (call.outcome == "Ordered") View.GONE else View.VISIBLE
            
            holder.btnLogAction.setOnClickListener {
                onActionClick(call)
            }

            val hasRecording = !call.recordingPath.isNullOrEmpty() && call.recordingPath.startsWith("content://")
            holder.playRecording.isEnabled = hasRecording
            holder.playRecording.alpha = if (hasRecording) 1.0f else 0.5f

            if (hasRecording) {
                if (position == currentlyPlayingPosition) {
                    holder.playRecording.text = "Stop"
                } else {
                    holder.playRecording.text = "Play"
                }
            } else {
                holder.playRecording.text = "Play"
            }

            // Detail Expansion Logic
            if (position == expandedPosition && (call.outcome != null || call.customerName != null)) {
                holder.detailContainer.visibility = View.VISIBLE
                holder.detailCustomerName.text = call.customerName ?: "Unknown Customer"
                
                holder.orderTable.removeAllViews()

                // 2. Sub-Header Row (Product | Quantity)
                val subHeaderRow = android.widget.TableRow(context)
                subHeaderRow.addView(createTableCell("Product", true))
                subHeaderRow.addView(createTableCell("Quantity", true))
                holder.orderTable.addView(subHeaderRow)

                // 3. Outcome Row (Always show)
                val outcomeRow = android.widget.TableRow(context)
                outcomeRow.addView(createTableCell("Status", false))
                outcomeRow.addView(createTableCell(call.outcome ?: "N/A", true))
                holder.orderTable.addView(outcomeRow)

                // 4. Order Rows
                if (call.outcome == "Ordered" && call.productQuantities != null) {
                    call.productQuantities.forEach { (name, qty) ->
                        val row = android.widget.TableRow(context)
                        row.addView(createTableCell(name, false))
                        row.addView(createTableCell(qty.toString(), true))
                        holder.orderTable.addView(row)
                    }
                    if (call.needBranding) {
                        val row = android.widget.TableRow(context)
                        val tv = createTableCell("Branding Required", true).apply {
                            layoutParams = android.widget.TableRow.LayoutParams().apply { span = 2 }
                        }
                        row.addView(tv)
                        holder.orderTable.addView(row)
                    }
                } else if (call.outcome == "Lost") {
                     val row = android.widget.TableRow(context)
                     row.addView(createTableCell("Reason", false))
                     row.addView(createTableCell(call.reasonForLoss ?: "N/A", true))
                     holder.orderTable.addView(row)
                }

                if (!call.remarks.isNullOrEmpty()) {
                    holder.detailRemarks.text = "Notes: ${call.remarks}"
                    holder.detailRemarks.visibility = View.VISIBLE
                } else {
                    holder.detailRemarks.visibility = View.GONE
                }
            } else {
                holder.detailContainer.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newCallHistory: List<CallHistory>) {
        items.clear()
        val sortedList = newCallHistory.sortedByDescending { it.timestamp }
        
        val dateFormat = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())
        var lastDate = ""
        
        for (call in sortedList) {
            val currentDate = dateFormat.format(Date(call.timestamp))
            if (currentDate != lastDate) {
                items.add(CallHistoryItem.Header(currentDate))
                lastDate = currentDate
            }
            items.add(CallHistoryItem.Call(call))
        }
        notifyDataSetChanged()
    }

    fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        val lastPlayingPosition = currentlyPlayingPosition
        currentlyPlayingPosition = null
        lastPlayingPosition?.let { notifyItemChanged(it) }
    }

    private fun onPlay(position: Int) {
        if (currentlyPlayingPosition == position) {
            stopPlayback()
            return
        }

        stopPlayback()

        val item = items[position]
        if (item is CallHistoryItem.Call && item.callHistory.recordingPath.startsWith("content://")) {
            try {
                val recordingUri = Uri.parse(item.callHistory.recordingPath)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(context, recordingUri)
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        mp.start()
                        currentlyPlayingPosition = position
                        notifyItemChanged(position)
                    }
                    setOnCompletionListener {
                        stopPlayback()
                    }
                    setOnErrorListener { _, _, _ ->
                        stopPlayback()
                        Toast.makeText(context, "Could not play recording", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(context, "Could not play recording", Toast.LENGTH_SHORT).show()
                stopPlayback()
            }
        } else {
            Toast.makeText(context, "No recording found for this call", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createTableCell(text: String, isBold: Boolean): TextView {
        return TextView(context).apply {
            this.text = text
            if (isBold) setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(context.getColor(R.color.ui_text_primary))
            textSize = 14f
            setPadding(8, 4, 8, 4)
            setBackgroundResource(R.drawable.bg_table_cell)
            layoutParams = android.widget.TableRow.LayoutParams(0, android.widget.TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }
    }
}
