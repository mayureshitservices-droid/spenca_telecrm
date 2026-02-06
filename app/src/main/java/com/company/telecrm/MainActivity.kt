package com.company.telecrm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import java.util.Calendar
import com.google.gson.Gson
import android.util.Log
import com.company.telecrm.api.*
import com.company.telecrm.utils.DeviceManager
import com.company.telecrm.workers.CallOutcomeWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var callHistoryAdapter: CallHistoryAdapter
    private lateinit var fullCallHistory: List<CallHistory>
    private lateinit var repository: CallHistoryRepository
    private lateinit var phoneNameRepository: PhoneNameRepository
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var selectedWhatsAppNumber: String? = null
    private lateinit var waTemplateRepository: WhatsAppTemplateRepository
    private lateinit var waTemplateAdapter: WhatsAppTemplateAdapter
    private var currentTemplates: MutableList<WhatsAppTemplate> = mutableListOf()

    
    private val callLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.company.telecrm.UPDATE_CALL_LOG") {
                loadCallHistoryAsync()
            }
        }
    }

    private val permissionsRequestCode = 101

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        return permissions.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        if (hasPermissions()) {
            setupUI()
        } else {
            requestPermissions()
        }
    }

    private fun hasPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), permissionsRequestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionsRequestCode) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                setupUI()
            } else {
                Toast.makeText(this, "Permissions are required for the app to function.", Toast.LENGTH_LONG).show()
                // Optionally, you can close the app or disable features
            }
        }
    }

    private fun setupUI() {
        checkAndRegisterDevice()

        // Start the Call Recording Service as soon as the app starts
        val serviceIntent = Intent(this, CallRecordingService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        val recyclerView = findViewById<RecyclerView>(R.id.call_history_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        repository = CallHistoryRepository(this)
        phoneNameRepository = PhoneNameRepository(this)
        // Initialize with an empty list
        fullCallHistory = emptyList()

        callHistoryAdapter = CallHistoryAdapter(this, mutableListOf(),
            { call -> showCallActionDialog(call) },
            { call -> startWhatsAppFlow(call.phoneNumber) }
        )
        recyclerView.adapter = callHistoryAdapter

        val remindersRecyclerView = findViewById<RecyclerView>(R.id.reminders_recycler_view)
        remindersRecyclerView.layoutManager = LinearLayoutManager(this)
        val remindersAdapter = CallHistoryAdapter(this, mutableListOf(),
            { call -> showCallActionDialog(call) },
            { call -> startWhatsAppFlow(call.phoneNumber) }
        )
        remindersRecyclerView.adapter = remindersAdapter

        val brandingRecyclerView = findViewById<RecyclerView>(R.id.branding_recycler_view)
        brandingRecyclerView.layoutManager = LinearLayoutManager(this)
        val brandingAdapter = CallHistoryAdapter(this, mutableListOf(),
            { call -> showCallActionDialog(call) },
            { call -> startWhatsAppFlow(call.phoneNumber) }
        )
        brandingRecyclerView.adapter = brandingAdapter

        val ordersRecyclerView = findViewById<RecyclerView>(R.id.orders_recycler_view)
        ordersRecyclerView.layoutManager = LinearLayoutManager(this)
        val ordersAdapter = CallHistoryAdapter(this, mutableListOf(),
            { call -> showCallActionDialog(call) },
            { call -> startWhatsAppFlow(call.phoneNumber) }
        )
        ordersRecyclerView.adapter = ordersAdapter

        waTemplateRepository = WhatsAppTemplateRepository(this)
        currentTemplates = waTemplateRepository.getTemplates().toMutableList()

        // Setup WhatsApp Templates RecyclerView
        val waRecyclerView = findViewById<RecyclerView>(R.id.whatsapp_templates_recycler_view)
        waRecyclerView.layoutManager = LinearLayoutManager(this)
        waTemplateAdapter = WhatsAppTemplateAdapter(
            currentTemplates,
            { template -> // On Click
                selectedWhatsAppNumber?.let { number ->
                    sendWhatsApp(number, template.message)
                } ?: run {
                    Toast.makeText(this, "Please select a contact from Call History first", Toast.LENGTH_LONG).show()
                    showTab(R.id.layout_dialer)
                }
            },
            { template -> // On Edit
                showEditTemplateDialog(template)
            },
            { template -> // On Delete
                showDeleteConfirmation(template)
            }
        )
        waRecyclerView.adapter = waTemplateAdapter

        findViewById<View>(R.id.btn_add_template).setOnClickListener {
            showEditTemplateDialog(null)
        }

        val bottomNav: com.google.android.material.bottomnavigation.BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_dashboard

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showTab(R.id.layout_branding)
                    refreshBrandingUI()
                    true
                }
                R.id.nav_dashboard -> {
                    showTab(R.id.layout_dashboard)
                    true
                }
                R.id.nav_dialer -> {
                    showTab(R.id.layout_dialer)
                    findViewById<EditText>(R.id.phone_number_input).requestFocus()
                    true
                }
                R.id.nav_reminders -> {
                    showTab(R.id.layout_reminders)
                    refreshRemindersUI()
                    true
                }
                R.id.nav_more -> {
                    showTab(R.id.layout_more)
                    true
                }
                else -> false
            }
        }

        // More tab sub-navigation
        findViewById<View>(R.id.card_orders).setOnClickListener {
            showTab(R.id.layout_orders)
            refreshOrdersUI()
        }

        findViewById<View>(R.id.card_whatsapp).setOnClickListener {
            selectedWhatsAppNumber = null
            findViewById<TextView>(R.id.wa_title)?.text = getString(R.string.whatsapp_title)
            showTab(R.id.layout_whatsapp)
        }

        findViewById<View>(R.id.btn_back_from_orders).setOnClickListener {
            showTab(R.id.layout_more)
        }

        findViewById<View>(R.id.btn_back_from_whatsapp).setOnClickListener {
            showTab(R.id.layout_more)
        }

        val searchView = findViewById<SearchView>(R.id.search_view)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val filteredList = fullCallHistory.filter {
                    it.phoneNumber.contains(newText ?: "", ignoreCase = true)
                }
                callHistoryAdapter.updateData(filteredList)
                return true
            }
        })

        val phoneNumberInput = findViewById<EditText>(R.id.phone_number_input)
        val callButton: ImageButton = findViewById(R.id.call_button)
        val fabDialer: FloatingActionButton = findViewById(R.id.fab_dialer)
        val dialerCard: View = findViewById(R.id.dialer_card)

        fabDialer.setOnClickListener {
            dialerCard.visibility = View.VISIBLE
            fabDialer.visibility = View.GONE
            phoneNumberInput.requestFocus()
        }

        callButton.setOnClickListener {
            val phoneNumber = phoneNumberInput.text.toString()
            if (phoneNumber.isNotEmpty()) {
                // Send the number to the service BEFORE making the call
                val serviceIntent = Intent(this, CallRecordingService::class.java)
                serviceIntent.putExtra("PHONE_NUMBER", phoneNumber)
                ContextCompat.startForegroundService(this, serviceIntent)

                val intent = Intent(Intent.ACTION_CALL, "tel:$phoneNumber".toUri())
                startActivity(intent)

                // Hide dialer after starting call
                dialerCard.visibility = View.GONE
                fabDialer.visibility = View.VISIBLE
                phoneNumberInput.text.clear()
            } else {
                Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<ImageButton>(R.id.btn_share_dashboard).setOnClickListener {
            shareDashboardReport()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermissions()) {
             loadCallHistoryAsync()
        }
        
        val filter = IntentFilter("com.company.telecrm.UPDATE_CALL_LOG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callLogReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(callLogReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(callLogReceiver)
    }

    override fun onStop() {
        super.onStop()
        callHistoryAdapter.stopPlayback()
    }

    private fun loadCallHistoryAsync() {
        executor.execute {
            val callHistory = repository.getCallHistory()
            handler.post {
                fullCallHistory = callHistory
                callHistoryAdapter.updateData(fullCallHistory)
                refreshDashboardStats(fullCallHistory)
                refreshRemindersUI()
                refreshOrdersUI()
                refreshBrandingUI()
            }
        }
    }

    private fun startWhatsAppFlow(phoneNumber: String) {
        selectedWhatsAppNumber = phoneNumber
        findViewById<TextView>(R.id.wa_title)?.text = getString(R.string.send_to, phoneNumber)
        showTab(R.id.layout_whatsapp)
    }

    private fun showEditTemplateDialog(template: WhatsAppTemplate?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_template, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.edit_template_title)
        val messageInput = dialogView.findViewById<EditText>(R.id.edit_template_message)

        template?.let {
            titleInput.setText(it.title)
            messageInput.setText(it.message)
        }

        val alertDialog = AlertDialog.Builder(this, R.style.Theme_Telecrm_DarkDialog)
            .setTitle(if (template == null) "Add Template" else "Edit Template")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = titleInput.text.toString()
                val newMessage = messageInput.text.toString()

                if (newTitle.isNotEmpty() && newMessage.isNotEmpty()) {
                    if (template == null) {
                        currentTemplates.add(WhatsAppTemplate(System.currentTimeMillis().toString(), newTitle, newMessage))
                    } else {
                        val index = currentTemplates.indexOfFirst { it.id == template.id }
                        if (index != -1) {
                            currentTemplates[index] = template.copy(title = newTitle, message = newMessage)
                        }
                    }
                    waTemplateRepository.saveTemplates(currentTemplates)
                    waTemplateAdapter.updateData(currentTemplates)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(template: WhatsAppTemplate) {
        AlertDialog.Builder(this)
            .setTitle("Delete Template")
            .setMessage("Are you sure you want to delete '${template.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                currentTemplates.removeAll { it.id == template.id }
                waTemplateRepository.saveTemplates(currentTemplates)
                waTemplateAdapter.updateData(currentTemplates)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendWhatsApp(phoneNumber: String, message: String) {
        try {
            val cleanedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            // Ensure number has country code if it doesn't start with + (assuming India +91 for now as per common use case, but better to be generic)
            val finalNumber = if (!cleanedNumber.startsWith("+")) {
                if (cleanedNumber.length == 10) "+91$cleanedNumber" else cleanedNumber
            } else {
                cleanedNumber
            }

            val intent = Intent(Intent.ACTION_VIEW)
            val url = "https://api.whatsapp.com/send?phone=$finalNumber&text=${java.net.URLEncoder.encode(message, "UTF-8")}"
            intent.data = url.toUri()
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTab(layoutId: Int) {
        // Reset dialer state when switching tabs
        if (layoutId == R.id.layout_dialer) {
            findViewById<View>(R.id.dialer_card).visibility = View.GONE
            findViewById<View>(R.id.fab_dialer).visibility = View.VISIBLE
        }

        val tabs = listOf(
            R.id.layout_dashboard,
            R.id.layout_dialer,
            R.id.layout_reminders,
            R.id.layout_orders,
            R.id.layout_branding,
            R.id.layout_more,
            R.id.layout_whatsapp
        )
        tabs.forEach { id ->
            findViewById<View>(id).visibility = if (id == layoutId) View.VISIBLE else View.GONE
        }
    }

    private fun refreshRemindersUI() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayMidnight = calendar.timeInMillis

        val upcomingReminders = fullCallHistory.filter {
            it.followUpDate != null && it.followUpDate >= todayMidnight
        }
        (findViewById<RecyclerView>(R.id.reminders_recycler_view).adapter as CallHistoryAdapter).updateData(upcomingReminders)
    }

    private fun refreshBrandingUI() {
        val brandingItems = fullCallHistory.filter { it.needBranding }
        (findViewById<RecyclerView>(R.id.branding_recycler_view).adapter as CallHistoryAdapter).updateData(brandingItems)
    }

    private fun refreshOrdersUI() {
        val orders = fullCallHistory.filter {
            it.outcome == "Ordered"
        }
        (findViewById<RecyclerView>(R.id.orders_recycler_view).adapter as CallHistoryAdapter).updateData(orders)
    }

    private fun refreshDashboardStats(history: List<CallHistory>) {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        val todayYear = calendar.get(java.util.Calendar.YEAR)
        val todayDayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)

        val todayHistory = history.filter {
            val callCal = java.util.Calendar.getInstance()
            callCal.timeInMillis = it.timestamp
            callCal.get(java.util.Calendar.YEAR) == todayYear &&
                    callCal.get(java.util.Calendar.DAY_OF_YEAR) == todayDayOfYear
        }

        // Today's Stats
        val todayTotalCalls = todayHistory.size
        val todayAnsweredCalls = todayHistory.count { it.status == "Answered" }
        val todayUnansweredCalls = todayTotalCalls - todayAnsweredCalls
        val todayTotalOrders = todayHistory.count { it.outcome == "Ordered" }
        val todayBrandingOrders = todayHistory.count { it.needBranding }
        val todayTotalDurationSeconds = todayHistory.sumOf { call ->
            try {
                val parts = call.callDuration.split(":").map { it.trim().toLong() }
                if (parts.size == 2) parts[0] * 60 + parts[1] else 0L
            } catch (e: Exception) { 0L }
        }

        findViewById<TextView>(R.id.day_total_calls).text = todayTotalCalls.toString()
        findViewById<TextView>(R.id.day_answered_calls).text = todayAnsweredCalls.toString()
        findViewById<TextView>(R.id.day_unanswered_calls).text = todayUnansweredCalls.toString()
        findViewById<TextView>(R.id.day_total_orders).text = todayTotalOrders.toString()
        findViewById<TextView>(R.id.day_branding_orders).text = todayBrandingOrders.toString()
        findViewById<TextView>(R.id.day_total_duration).text = "${todayTotalDurationSeconds / 60}m ${todayTotalDurationSeconds % 60}s"

        // Monthly Overview (Total for now as per current logic)
        val monthTotalCalls = history.size
        val monthAnsweredCalls = history.count { it.status == "Answered" }
        val monthUnansweredCalls = monthTotalCalls - monthAnsweredCalls
        val monthTotalOrders = history.count { it.outcome == "Ordered" }
        val monthBrandingOrders = history.count { it.needBranding }
        
        val monthTotalDurationSeconds = history.sumOf { call ->
            try {
                val parts = call.callDuration.split(":").map { it.trim().toLong() }
                if (parts.size == 2) parts[0] * 60 + parts[1] else 0L
            } catch (e: Exception) { 0L }
        }

        findViewById<TextView>(R.id.month_total_calls).text = monthTotalCalls.toString()
        findViewById<TextView>(R.id.month_answered_calls).text = monthAnsweredCalls.toString()
        findViewById<TextView>(R.id.month_unanswered_calls).text = monthUnansweredCalls.toString()
        findViewById<TextView>(R.id.month_total_orders).text = monthTotalOrders.toString()
        findViewById<TextView>(R.id.month_branding_orders).text = monthBrandingOrders.toString()
        findViewById<TextView>(R.id.month_total_duration).text = getString(R.string.call_duration_format, monthTotalDurationSeconds / 60, monthTotalDurationSeconds % 60)
    }

    private fun showCallActionDialog(call: CallHistory) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_call_action, null)
        val etCustomerName = dialogView.findViewById<TextInputEditText>(R.id.et_customer_name)
        val spinnerOutcome = dialogView.findViewById<Spinner>(R.id.spinner_outcome)

        val layoutOrdered = dialogView.findViewById<LinearLayout>(R.id.layout_ordered)
        val layoutCallLater = dialogView.findViewById<LinearLayout>(R.id.layout_call_later)
        val layoutOtherConcerns = dialogView.findViewById<LinearLayout>(R.id.layout_other_concerns)
        val layoutLost = dialogView.findViewById<LinearLayout>(R.id.layout_lost)

        val etNextFollowUp = dialogView.findViewById<TextInputEditText>(R.id.et_next_follow_up)
        val etFollowUpDays = dialogView.findViewById<TextInputEditText>(R.id.et_follow_up_days)
        val etRemarks = dialogView.findViewById<TextInputEditText>(R.id.et_remarks)
        val etReasonLoss = dialogView.findViewById<TextInputEditText>(R.id.et_reason_loss)
        val cbBranding = dialogView.findViewById<CheckBox>(R.id.cb_branding)
        val spinnerDistributor = dialogView.findViewById<Spinner>(R.id.spinner_distributor)

        // Pre-fill if exists
        etCustomerName.setText(call.customerName)

        val outcomes = arrayOf("Select Outcome", "Ordered", "Call Later", "Other Concerns", "Lost")
        val adapter = ArrayAdapter(this, R.layout.item_spinner, outcomes)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        spinnerOutcome.adapter = adapter

        // Distributor Spinner
        val distributorAdapter = ArrayAdapter.createFromResource(this, R.array.distributor_list, R.layout.item_spinner)
        distributorAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        spinnerDistributor.adapter = distributorAdapter

        spinnerOutcome.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = outcomes[position]
                layoutOrdered.visibility = if (selected == "Ordered") View.VISIBLE else View.GONE
                layoutCallLater.visibility = if (selected == "Call Later") View.VISIBLE else View.GONE
                layoutOtherConcerns.visibility = if (selected == "Other Concerns") View.VISIBLE else View.GONE
                layoutLost.visibility = if (selected == "Lost") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val alertDialog = AlertDialog.Builder(this, R.style.Theme_Telecrm_DarkDialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btn_save_action).setOnClickListener {
            val customerName = etCustomerName.text.toString().trim()
            if (customerName.isEmpty()) {
                Toast.makeText(this, "Customer Name is required", Toast.LENGTH_SHORT).show()
                etCustomerName.requestFocus()
                return@setOnClickListener
            }

            val outcome = spinnerOutcome.selectedItem.toString()
            if (outcome == "Select Outcome") {
                Toast.makeText(this, "Please select an outcome", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Persistence: save name globally for this phone number
            phoneNameRepository.saveName(call.phoneNumber, customerName)
            var followUpDate: Long? = null
            var reasonLoss: String? = null
            var remarks: String? = null
            var needBranding = false
            var selectedDistributor: String? = null
            val productQuantities = mutableMapOf<String, Int>()

            when (outcome) {
                "Ordered" -> {
                    needBranding = cbBranding.isChecked
                    val days = etNextFollowUp.text.toString().toIntOrNull() ?: -1
                    if (days >= 0) {
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DAY_OF_YEAR, days)
                        followUpDate = cal.timeInMillis
                    }
                    
                    // Helper to get product quantity
                    fun getQty(rowId: Int, name: String) {
                        val row = dialogView.findViewById<View>(rowId)
                        val qty = row.findViewById<EditText>(R.id.et_quantity).text.toString().toIntOrNull() ?: 0
                        if (qty > 0) productQuantities[name] = qty
                        row.findViewById<TextView>(R.id.tv_product_name).text = name
                    }

                    getQty(R.id.row_reg_1l, "Regular 1 Ltrs")
                    getQty(R.id.row_reg_2l, "Regular 2 Ltrs")
                    getQty(R.id.row_reg_500ml, "Regular 500 ml")
                    getQty(R.id.row_prem_1l, "Premium 1 Ltrs")
                    getQty(R.id.row_prem_500ml, "Premium 500 ml")
                    getQty(R.id.row_prem_250ml, "Premium 250 ml")
                    getQty(R.id.row_square_1l, "Square Bottle 1 Ltrs")
                    getQty(R.id.row_square_500ml, "Square Bottle 500 ml")
                    getQty(R.id.row_square_200ml, "Square Bottle 200 ml")
                    getQty(R.id.row_alkaline, "Alkaline Water")
                    
                    selectedDistributor = spinnerDistributor.selectedItem.toString()
                }
                "Call Later" -> {
                    val days = etFollowUpDays.text.toString().toIntOrNull() ?: -1
                    if (days >= 0) {
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DAY_OF_YEAR, days)
                        followUpDate = cal.timeInMillis
                    }
                }
                "Other Concerns" -> {
                    remarks = etRemarks.text.toString()
                }
                "Lost" -> {
                    reasonLoss = etReasonLoss.text.toString()
                }
            }

            val updatedCall = call.copy(
                customerName = customerName,
                outcome = outcome,
                followUpDate = followUpDate,
                remarks = remarks,
                productQuantities = if (productQuantities.isEmpty()) null else productQuantities,
                needBranding = needBranding,
                reasonForLoss = reasonLoss,
                distributor = selectedDistributor
            )

            executor.execute {
                repository.saveCallHistory(updatedCall)
                
                // Sync Outcome to Server
                syncOutcomeToServer(updatedCall)

                handler.post {
                    loadCallHistoryAsync()
                    alertDialog.dismiss()
                    Toast.makeText(this, "Action logged successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }

        alertDialog.show()
        
        // Finalize product names in rows (since we use include)
        dialogView.findViewById<View>(R.id.row_reg_1l).apply { findViewById<TextView>(R.id.tv_product_name).text = "Regular 1 Ltrs" }
        dialogView.findViewById<View>(R.id.row_reg_2l).apply { findViewById<TextView>(R.id.tv_product_name).text = "Regular 2 Ltrs" }
        dialogView.findViewById<View>(R.id.row_reg_500ml).apply { findViewById<TextView>(R.id.tv_product_name).text = "Regular 500 ml" }
        dialogView.findViewById<View>(R.id.row_prem_1l).apply { findViewById<TextView>(R.id.tv_product_name).text = "Premium 1 Ltrs" }
        dialogView.findViewById<View>(R.id.row_prem_500ml).apply { findViewById<TextView>(R.id.tv_product_name).text = "Premium 500 ml" }
        dialogView.findViewById<View>(R.id.row_prem_250ml).apply { findViewById<TextView>(R.id.tv_product_name).text = "Premium 250 ml" }
        dialogView.findViewById<View>(R.id.row_square_1l).apply { findViewById<TextView>(R.id.tv_product_name).text = "Square Bottle 1 Ltrs" }
        dialogView.findViewById<View>(R.id.row_square_500ml).apply { findViewById<TextView>(R.id.tv_product_name).text = "Square Bottle 500 ml" }
        dialogView.findViewById<View>(R.id.row_square_200ml).apply { findViewById<TextView>(R.id.tv_product_name).text = "Square Bottle 200 ml" }
        dialogView.findViewById<View>(R.id.row_alkaline).apply { findViewById<TextView>(R.id.tv_product_name).text = "Alkaline Water" }
    }

    private fun syncOutcomeToServer(call: CallHistory) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncData = Data.Builder()
            .putString("CALL_ID", call.callId)
            .putString("CUSTOMER_NAME", call.customerName)
            .putString("OUTCOME", call.outcome)
            .putString("REMARKS", call.remarks)
            .putLong("FOLLOW_UP_DATE", call.followUpDate ?: 0)
            .putString("PRODUCT_QUANTITIES", Gson().toJson(call.productQuantities))
            .putBoolean("NEED_BRANDING", call.needBranding)
            .putString("REASON_FOR_LOSS", call.reasonForLoss)
            .putString("DISTRIBUTOR", call.distributor)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<CallOutcomeWorker>()
            .setConstraints(constraints)
            .setInputData(syncData)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(syncRequest)
        Log.d("MainActivity", "Enqueued CallOutcomeWorker for ${call.callId}")
    }

    private fun shareDashboardReport() {
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.layout_dashboard)
        
        // Capture screenshot of the scrollable content
        val bitmap = captureScreenshot(scrollView)
        
        try {
            val cachePath = java.io.File(cacheDir, "images")
            cachePath.mkdirs()
            val stream = java.io.FileOutputStream("$cachePath/dashboard_report.jpg")
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            stream.close()

            val imageFile = java.io.File(cachePath, "dashboard_report.jpg")
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this, 
                "${packageName}.provider", 
                imageFile
            )

            if (contentUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                shareIntent.type = "image/jpeg"
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
                shareIntent.setPackage("com.whatsapp")
                
                try {
                    startActivity(shareIntent)
                } catch (e: Exception) {
                    // Fallback to generic share if WhatsApp is not installed
                    shareIntent.setPackage(null)
                    startActivity(Intent.createChooser(shareIntent, "Share Report"))
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error sharing report", e)
            Toast.makeText(this, "Error generating report", Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureScreenshot(scrollview: androidx.core.widget.NestedScrollView): android.graphics.Bitmap {
        val child = scrollview.getChildAt(0)
        val height = child.height
        val width = child.width
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        child.draw(canvas)
        return bitmap
    }

    private fun checkAndRegisterDevice() {
        DeviceManager.init(this)
        if (DeviceManager.isRegistered()) {
            DeviceManager.startHeartbeat(this)
            return
        }

        DeviceManager.registerDevice { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Device Registered Successfully", Toast.LENGTH_LONG).show()
                    DeviceManager.startHeartbeat(this)
                } else {
                    Toast.makeText(this, "Device Registration Failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}