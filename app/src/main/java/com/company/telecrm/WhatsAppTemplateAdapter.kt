package com.company.telecrm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WhatsAppTemplateAdapter(
    private var templates: List<WhatsAppTemplate>,
    private val onTemplateClick: (WhatsAppTemplate) -> Unit,
    private val onEditClick: (WhatsAppTemplate) -> Unit,
    private val onDeleteClick: (WhatsAppTemplate) -> Unit
) : RecyclerView.Adapter<WhatsAppTemplateAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.template_title)
        val message: TextView = view.findViewById(R.id.template_message)
        val btnEdit: ImageButton = view.findViewById(R.id.btn_edit_template)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_template)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.whatsapp_template_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val template = templates[position]
        holder.title.text = template.title
        holder.message.text = template.message
        
        holder.itemView.setOnClickListener {
            onTemplateClick(template)
        }
        
        holder.btnEdit.setOnClickListener {
            onEditClick(template)
        }
        
        holder.btnDelete.setOnClickListener {
            onDeleteClick(template)
        }
    }

    override fun getItemCount() = templates.size

    fun updateData(newTemplates: List<WhatsAppTemplate>) {
        templates = newTemplates
        notifyDataSetChanged()
    }
}
