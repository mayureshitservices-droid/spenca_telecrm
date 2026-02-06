package com.company.telecrm

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WhatsAppTemplateRepository(context: Context) {
    private val prefs = context.getSharedPreferences("whatsapp_templates", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getTemplates(): List<WhatsAppTemplate> {
        val json = prefs.getString("templates_list", null) ?: return getDefaultTemplates()
        val type = object : TypeToken<List<WhatsAppTemplate>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveTemplates(templates: List<WhatsAppTemplate>) {
        val json = gson.toJson(templates)
        prefs.edit().putString("templates_list", json).apply()
    }

    private fun getDefaultTemplates(): List<WhatsAppTemplate> {
        return listOf(
            WhatsAppTemplate("1", "Spenca Introduction", "Greetings from Spenca Water Services!\n\nWe provide premium drinking water solutions across Maharashtra. \n\n📍 SOLAPUR: Anudeep Towers, Saat Rasta.\n📍 PUNE: Lohia Jain Avenue, Kothrud.\n\n📞 Toll-free: 18008330712\n📱 +91 9765991857 / +91 7755956761\n\nHow can we serve you today?"),
            WhatsAppTemplate("2", "Order Success", "Thank you for choosing Spenca Water! 💧\n\nYour order has been recorded successfully. Our team will contact you shortly for delivery scheduling.\n\nStay Hydrated, Stay Healthy!"),
            WhatsAppTemplate("3", "Office Addresses", "Our Office Locations:\n\n🏢 SOLAPUR: House No. 158/C, 2nd Floor, Opposite Yatiraj Hotel, Saat Rasta, Station Rd, Solapur - 413001.\n\n🏢 PUNE: 2nd floor, Lohia Jain avenue, Paud Road, Chandani Chowk, Kothrud, Pune - 411038."),
            WhatsAppTemplate("4", "Product Range", "Spenca Water Product List:\n✅ Regular (500ml, 1L, 2L)\n✅ Premium (250ml, 500ml, 1L)\n✅ Square (200ml, 500ml, 1L)\n✅ Alkaline Water ⚡\n\nWhich one would you like to order?"),
            WhatsAppTemplate("5", "Post-Call Summary", "It was a pleasure speaking with you! 🙏\n\nAs discussed, we've updated your requirements. Please save our number for quick orders on WhatsApp.\n\nTeam Spenca Water")
        )
    }
}
