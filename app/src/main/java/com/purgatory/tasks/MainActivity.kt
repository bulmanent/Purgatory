package com.purgatory.tasks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.purgatory.tasks.databinding.ActivityMainBinding
import com.purgatory.tasks.databinding.DialogEditTaskBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val repository = SheetsRepository()
    private val taskAdapter = TaskAdapter(
        onActionClicked = { task -> toggleComplete(task) },
        onItemClicked = { task -> showTaskDialog(task) }
    )

    private var allTasks: List<Task> = emptyList()
    private var ownerFilter: String? = null
    private var statusFilter: TaskStatus? = null
    private var viewMode: ViewMode = ViewMode.CURRENT

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val signInTask = GoogleSignIn.getSignedInAccountFromIntent(it.data)
        try {
            signInTask.getResult(ApiException::class.java)
            refreshTasks()
        } catch (ex: ApiException) {
            Toast.makeText(this, ex.message ?: "Sign-in failed.", Toast.LENGTH_LONG).show()
            showSignInState(showSignIn = true, showSpreadsheetHint = false)
        }
    }

    private val recoverAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshTasks()
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.mainToolbar)

        binding.taskList.layoutManager = LinearLayoutManager(this)
        binding.taskList.adapter = taskAdapter

        binding.viewToggleGroup.check(R.id.viewCurrentButton)
        binding.viewToggleGroup.addOnButtonCheckedListener { _, checkedId, _ ->
            viewMode = when (checkedId) {
                R.id.viewCompletedButton -> ViewMode.COMPLETED
                R.id.viewAllButton -> ViewMode.ALL
                else -> ViewMode.CURRENT
            }
            applyFilters()
        }

        setupFilters()

        binding.addTaskFab.setOnClickListener { showTaskDialog(null) }
        binding.retryButton.setOnClickListener { refreshTasks() }
        binding.signInButton.setOnClickListener { startSignIn() }
        binding.taskRefresh.setOnRefreshListener { refreshTasks() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupFilters()
        refreshTasks()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_sync -> {
                refreshTasks()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupFilters() {
        val ownerOptions = mutableListOf(getString(R.string.filter_all))
        ownerOptions.addAll(AppUsers.all.map { it.displayName })
        binding.ownerFilterInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, ownerOptions)
        )

        val defaultOwner = AppSettings.getDefaultUser(this)
        binding.ownerFilterInput.setText(defaultOwner ?: getString(R.string.filter_all), false)
        ownerFilter = if (defaultOwner.isNullOrBlank()) null else defaultOwner
        binding.ownerFilterInput.setOnItemClickListener { _, _, position, _ ->
            ownerFilter = if (position == 0) null else ownerOptions[position]
            applyFilters()
        }

        val statusOptions = listOf(
            getString(R.string.filter_all),
            getString(R.string.task_status_unassigned),
            getString(R.string.task_status_due),
            getString(R.string.task_status_crucial),
            getString(R.string.task_status_complete)
        )
        binding.statusFilterInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, statusOptions)
        )
        binding.statusFilterInput.setText(getString(R.string.filter_all), false)
        statusFilter = null
        binding.statusFilterInput.setOnItemClickListener { _, _, position, _ ->
            statusFilter = when (position) {
                1 -> TaskStatus.UNASSIGNED
                2 -> TaskStatus.DUE
                3 -> TaskStatus.CRUCIAL
                4 -> TaskStatus.COMPLETE
                else -> null
            }
            applyFilters()
        }
    }

    private fun refreshTasks() {
        val spreadsheetId = AppSettings.getSpreadsheetId(this)
        val account = AuthManager.getSignedInAccount(this)

        if (spreadsheetId.isNullOrBlank()) {
            showSignInState(showSignIn = true, showSpreadsheetHint = true)
            binding.taskRefresh.isRefreshing = false
            return
        }
        if (account == null) {
            showSignInState(showSignIn = true, showSpreadsheetHint = false)
            binding.taskRefresh.isRefreshing = false
            return
        }

        binding.taskRefresh.isRefreshing = true
        showLoading(true)
        lifecycleScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    AuthManager.getAccessToken(this@MainActivity, account)
                }
                val tasks = withContext(Dispatchers.IO) {
                    repository.loadTasks(token, spreadsheetId)
                }
                allTasks = tasks
                showSignInState(showSignIn = false, showSpreadsheetHint = false)
                applyFilters()
            } catch (recoverable: UserRecoverableAuthException) {
                recoverAuthLauncher.launch(recoverable.intent)
            } catch (ex: Exception) {
                showSignInState(showSignIn = true, showSpreadsheetHint = false)
                Toast.makeText(this@MainActivity, ex.message ?: "Unable to load tasks.", Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
                binding.taskRefresh.isRefreshing = false
            }
        }
    }

    private fun startSignIn() {
        val intent = AuthManager.getSignInClient(this).signInIntent
        signInLauncher.launch(intent)
    }

    private fun showLoading(isLoading: Boolean) {
        binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showSignInState(showSignIn: Boolean, showSpreadsheetHint: Boolean) {
        binding.signInContainer.visibility = if (showSignIn) View.VISIBLE else View.GONE
        binding.spreadsheetMessage.visibility = if (showSpreadsheetHint) View.VISIBLE else View.GONE
        binding.filterContainer.visibility = if (showSignIn) View.GONE else View.VISIBLE
        binding.taskRefresh.visibility = if (showSignIn) View.GONE else View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.addTaskFab.visibility = if (showSignIn) View.GONE else View.VISIBLE
    }

    private fun applyFilters() {
        val filtered = allTasks
            .filter { task ->
                when (viewMode) {
                    ViewMode.COMPLETED -> task.status == TaskStatus.COMPLETE
                    ViewMode.ALL -> true
                    ViewMode.CURRENT -> isCurrentTask(task)
                }
            }
            .filter { task ->
                ownerFilter == null || task.owner?.displayName.equals(ownerFilter, ignoreCase = true)
            }
            .filter { task ->
                statusFilter == null || task.status == statusFilter
            }
            .sortedWith(compareBy<Task> { statusSortKey(it.status) }
                .thenBy { it.dueDate ?: LocalDate.MAX }
                .thenByDescending { it.importance })

        taskAdapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.taskRefresh.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.signInContainer.visibility = View.GONE
        binding.addTaskFab.visibility = View.VISIBLE
    }

    private fun statusSortKey(status: TaskStatus): Int {
        return when (status) {
            TaskStatus.CRUCIAL -> 0
            TaskStatus.DUE -> 1
            TaskStatus.UNASSIGNED -> 2
            TaskStatus.COMPLETE -> 3
        }
    }

    private fun isCurrentTask(task: Task): Boolean {
        if (task.status == TaskStatus.CRUCIAL) return true
        if (task.status == TaskStatus.COMPLETE) return false
        val date = task.dueDate ?: return false
        val today = LocalDate.now()
        return date.isBefore(today) || !date.isAfter(today.plusDays(7))
    }

    private fun toggleComplete(task: Task) {
        val spreadsheetId = AppSettings.getSpreadsheetId(this) ?: return
        val account = AuthManager.getSignedInAccount(this) ?: return
        val updated = task.copy(
            status = if (task.status == TaskStatus.COMPLETE) TaskStatus.DUE else TaskStatus.COMPLETE
        )
        lifecycleScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    AuthManager.getAccessToken(this@MainActivity, account)
                }
                withContext(Dispatchers.IO) {
                    repository.updateTask(token, spreadsheetId, updated)
                }
                refreshTasks()
            } catch (recoverable: UserRecoverableAuthException) {
                recoverAuthLauncher.launch(recoverable.intent)
            } catch (ex: Exception) {
                Toast.makeText(this@MainActivity, ex.message ?: "Unable to update task.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showTaskDialog(task: Task?) {
        val dialogBinding = DialogEditTaskBinding.inflate(layoutInflater)
        val owners = AppUsers.all.map { it.displayName }
        val statuses = listOf(
            getString(R.string.task_status_unassigned),
            getString(R.string.task_status_due),
            getString(R.string.task_status_crucial),
            getString(R.string.task_status_complete)
        )

        dialogBinding.taskOwnerInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, owners)
        )
        dialogBinding.taskStatusInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, statuses)
        )

        if (task != null) {
            dialogBinding.taskDetailsInput.setText(task.details)
            dialogBinding.taskImportanceInput.setText(task.importance.toString())
            dialogBinding.taskOwnerInput.setText(task.owner?.displayName ?: owners.firstOrNull(), false)
            dialogBinding.taskStatusInput.setText(task.status.sheetValue, false)
            dialogBinding.taskDateInput.setText(DateUtils.format(task.dueDate))
        } else {
            dialogBinding.taskImportanceInput.setText("0")
            val defaultOwner = AppSettings.getDefaultUser(this)
            dialogBinding.taskOwnerInput.setText(defaultOwner ?: owners.firstOrNull(), false)
            dialogBinding.taskStatusInput.setText(getString(R.string.task_status_due), false)
            dialogBinding.taskDateInput.setText(DateUtils.format(LocalDate.now()))
        }

        fun updateDateField(status: TaskStatus) {
            if (status == TaskStatus.UNASSIGNED) {
                dialogBinding.taskDateInput.setText(DateUtils.format(DateUtils.unassignedDate()))
                dialogBinding.taskDateInput.isEnabled = false
            } else {
                if (!dialogBinding.taskDateInput.isEnabled) {
                    dialogBinding.taskDateInput.setText(DateUtils.format(LocalDate.now()))
                }
                dialogBinding.taskDateInput.isEnabled = true
            }
        }

        val initialStatus = TaskStatus.fromSheet(dialogBinding.taskStatusInput.text?.toString())
        updateDateField(initialStatus)

        dialogBinding.taskStatusInput.setOnItemClickListener { _, _, position, _ ->
            val status = when (position) {
                0 -> TaskStatus.UNASSIGNED
                1 -> TaskStatus.DUE
                2 -> TaskStatus.CRUCIAL
                3 -> TaskStatus.COMPLETE
                else -> TaskStatus.DUE
            }
            updateDateField(status)
        }

        dialogBinding.taskDateInput.setOnClickListener {
            if (dialogBinding.taskDateInput.isEnabled) {
                showDatePicker(dialogBinding)
            }
        }
        dialogBinding.taskDateInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && dialogBinding.taskDateInput.isEnabled) {
                showDatePicker(dialogBinding)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (task == null) R.string.add_task else R.string.edit_task)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.task_save) { _, _ ->
                saveTaskFromDialog(task, dialogBinding)
            }
            .setNegativeButton(R.string.task_cancel, null)
            .show()
    }

    private fun showDatePicker(dialogBinding: DialogEditTaskBinding) {
        val currentDate = DateUtils.parse(dialogBinding.taskDateInput.text?.toString()) ?: LocalDate.now()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentDate.year)
            set(Calendar.MONTH, currentDate.monthValue - 1)
            set(Calendar.DAY_OF_MONTH, currentDate.dayOfMonth)
        }
        val picker = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selected = LocalDate.of(year, month + 1, dayOfMonth)
                dialogBinding.taskDateInput.setText(DateUtils.format(selected))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        picker.show()
    }

    private fun saveTaskFromDialog(task: Task?, dialogBinding: DialogEditTaskBinding) {
        val details = dialogBinding.taskDetailsInput.text?.toString()?.trim().orEmpty()
        if (details.isBlank()) {
            Toast.makeText(this, "Details are required.", Toast.LENGTH_SHORT).show()
            return
        }
        val importance = dialogBinding.taskImportanceInput.text?.toString()?.toIntOrNull() ?: 0
        val owner = AppUsers.byDisplayName(dialogBinding.taskOwnerInput.text?.toString())
        val status = TaskStatus.fromSheet(dialogBinding.taskStatusInput.text?.toString())
        val date = if (status == TaskStatus.UNASSIGNED) {
            DateUtils.unassignedDate()
        } else {
            DateUtils.parse(dialogBinding.taskDateInput.text?.toString())
        }

        val spreadsheetId = AppSettings.getSpreadsheetId(this)
        val account = AuthManager.getSignedInAccount(this)
        if (spreadsheetId.isNullOrBlank() || account == null) {
            Toast.makeText(this, "Sign in and set Spreadsheet ID first.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    AuthManager.getAccessToken(this@MainActivity, account)
                }
                withContext(Dispatchers.IO) {
                    if (task == null) {
                        repository.addTask(token, spreadsheetId, details, importance, owner, status, date)
                    } else {
                        repository.updateTask(
                            token,
                            spreadsheetId,
                            task.copy(
                                details = details,
                                importance = importance,
                                owner = owner,
                                status = status,
                                dueDate = date
                            )
                        )
                    }
                }
                refreshTasks()
            } catch (recoverable: UserRecoverableAuthException) {
                recoverAuthLauncher.launch(recoverable.intent)
            } catch (ex: Exception) {
                Toast.makeText(this@MainActivity, ex.message ?: "Unable to save task.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private enum class ViewMode {
        CURRENT,
        COMPLETED,
        ALL
    }
}
