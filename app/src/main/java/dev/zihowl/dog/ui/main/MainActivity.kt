package dev.zihowl.dog.ui.main

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.AuthClient
import dev.zihowl.dog.data.remote.RealtimeClient
import dev.zihowl.dog.data.session.RoleMapper
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.data.sync.SyncStatusManager
import kotlinx.coroutines.launch
import dev.zihowl.dog.ui.ViewModelFactory
import dev.zihowl.dog.ui.notes.AddNoteDialogFragment
import dev.zihowl.dog.ui.notes.NotesViewModel
import dev.zihowl.dog.ui.subjects.AddSubjectDialogFragment
import dev.zihowl.dog.ui.subjects.SubjectsViewModel
import dev.zihowl.dog.ui.subjects.detail.SubjectDetailFragment
import dev.zihowl.dog.ui.schedule.ScheduleViewModel
import dev.zihowl.dog.ui.schedule.setup.ScheduleSetupFragment
import dev.zihowl.dog.ui.performance.PerformanceFragment
import dev.zihowl.dog.ui.tasks.AddTaskDialogFragment
import dev.zihowl.dog.ui.tasks.TasksViewModel

class MainActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener,
    FragmentManager.OnBackStackChangedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var tabLayout: TabLayout
    private lateinit var contentMainView: View
    private lateinit var fragmentContainer: View

    private lateinit var repository: dev.zihowl.dog.data.repository.DogRepository
    private lateinit var subjectsViewModel: SubjectsViewModel
    private lateinit var tasksViewModel: TasksViewModel
    private lateinit var notesViewModel: NotesViewModel
    private lateinit var scheduleViewModel: ScheduleViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var syncStatusManager: SyncStatusManager
    private lateinit var pagerAdapter: ViewPagerAdapter

    private val authClient = AuthClient()
    private var realtimeClient: RealtimeClient? = null
    @Volatile
    private var isRefreshingRole = false
    /** Evita mostrar el diálogo de sesión inválida más de una vez. */
    private var sessionInvalidHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        drawerLayout = findViewById(R.id.drawer_layout)
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        contentMainView = findViewById(R.id.contentMainLayout)
        fragmentContainer = findViewById(R.id.fragment_container)

        sessionManager = SessionManager(this)

        val initialTabRequest = intent?.getIntExtra(EXTRA_OPEN_TAB, -1) ?: -1
        intent?.removeExtra(EXTRA_OPEN_TAB)

        setupViewModels()
        setupToolbarAndDrawer()
        applyRoleVisibility()
        publishAcademicProfile()
        setupSyncStatus()
        setupNetworkReconnectSync()
        setupAuthButtons()
        setupViewPagerAndTabs(resolveLegacyTabIndex(initialTabRequest))

        supportFragmentManager.addOnBackStackChangedListener(this)

        startRealtimeRoleSync()
        syncOfficialSchedule()
        requestNotificationPermissionIfNeeded()

        if (intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            intent.removeExtra(EXTRA_OPEN_NOTIFICATIONS)
            showNotificationsFragment()
        }
    }

    /**
     * Permiso de notificaciones en runtime (Android 13+). Necesario para que
     * lleguen los avisos de cambios de horario (RQF-APP-27/28/29).
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { /* el usuario decide; sin acción adicional */ }

    private fun requestNotificationPermissionIfNeeded() {
        if (sessionManager.isGuestMode) return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Importa/actualiza las materias oficiales (solo lectura). El docente
     * recibe automáticamente las materias que imparte; el alumno, las de su
     * grupo/subgrupo configurado. No hace nada en modo invitado o sin sesión.
     */
    private fun syncOfficialSchedule() {
        if (sessionManager.isGuestMode || !sessionManager.isLoggedIn) return
        if (!::repository.isInitialized) return
        val weekDays = resources.getStringArray(R.array.week_days).toList()
        val syncer = dev.zihowl.dog.data.repository.OfficialScheduleSyncer(
            repository, sessionManager, weekDays,
            appContext = applicationContext
        )
        lifecycleScope.launch {
            val result = if (sessionManager.role == SessionManager.ROLE_DOCENTE) {
                syncer.syncForTeacher()
            } else {
                syncer.syncForStudent()
            }
            if (result is dev.zihowl.dog.data.repository.OfficialScheduleSyncer.Result.SessionInvalid) {
                handleSessionInvalid()
            }
        }
    }

    /**
     * El servidor rechazó la sesión: la cuenta ya no existe o el token es
     * inválido. Avisa al usuario una sola vez y, al confirmar, limpia las
     * materias oficiales "fantasma" y lo lleva a iniciar sesión de nuevo.
     */
    private fun handleSessionInvalid() {
        if (sessionInvalidHandled || isFinishing) return
        sessionInvalidHandled = true
        val owner = sessionManager.currentOwner()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.session_invalid_title)
            .setMessage(R.string.session_invalid_message)
            .setCancelable(false)
            .setPositiveButton(R.string.session_invalid_action) { _, _ ->
                finalizeSessionInvalid(owner)
            }
            .show()
    }

    /**
     * Limpieza al invalidar la sesión: borra solo las materias oficiales y los
     * datos de sesión (conserva materias manuales, tareas y notas del owner).
     * No respalda en el servidor porque la cuenta ya no es válida.
     */
    private fun finalizeSessionInvalid(owner: String) {
        lifecycleScope.launch {
            repository.clearOfficialSubjects(owner)
            sessionManager.isLoggedIn = false
            sessionManager.accessToken = null
            sessionManager.tokenExpiresAt = 0L
            navigateToServerConnection()
        }
    }

    /**
     * Escucha cambios en el servidor (RealtimeEvents) y refresca el rol del
     * usuario al instante si el catálogo de docentes lo modificó.
     */
    private fun startRealtimeRoleSync() {
        if (sessionManager.isGuestMode || !sessionManager.isLoggedIn) return
        val baseUrl = sessionManager.serverBaseUrl ?: return
        realtimeClient = RealtimeClient().also { client ->
            client.start(baseUrl) { scopes ->
                val rolesMayHaveChanged = scopes.any {
                    it.equals("USERS", ignoreCase = true) ||
                        it.equals("TEACHERS", ignoreCase = true)
                }
                if (rolesMayHaveChanged) {
                    runOnUiThread { refreshRoleFromServer(baseUrl) }
                }
                val schedulesChanged = scopes.any { it.equals("SCHEDULES", ignoreCase = true) }
                if (schedulesChanged) {
                    runOnUiThread { syncOfficialSchedule() }
                }
            }
        }
    }

    private fun refreshRoleFromServer(baseUrl: String) {
        if (isRefreshingRole) return
        val token = sessionManager.accessToken ?: return
        isRefreshingRole = true
        lifecycleScope.launch {
            val result = authClient.refreshSession(baseUrl, token)
            isRefreshingRole = false
            if (result is AuthClient.LoginResult.Success) {
                val newRole = RoleMapper.fromServer(result.serverRole)
                if (newRole != SessionManager.ROLE_UNSUPPORTED && newRole != sessionManager.role) {
                    sessionManager.accessToken = result.accessToken
                    sessionManager.tokenExpiresAt =
                        System.currentTimeMillis() + result.expiresIn * 1000
                    sessionManager.role = newRole
                    recreate()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) {
            intent.removeExtra(EXTRA_OPEN_NOTIFICATIONS)
            showNotificationsFragment()
            return
        }
        val tab = resolveLegacyTabIndex(intent.getIntExtra(EXTRA_OPEN_TAB, -1))
        if (tab >= 0) {
            viewPager.setCurrentItem(tab, false)
            updateTitleBasedOnPage(tab)
            intent.removeExtra(EXTRA_OPEN_TAB)
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "dev.zihowl.dog.extra.OPEN_TAB"
        const val EXTRA_OPEN_NOTIFICATIONS = "dev.zihowl.dog.extra.OPEN_NOTIFICATIONS"
        const val TAB_TASKS = 1
        const val TAB_NOTES = 2
        const val TAB_SCHEDULE = 3
    }

    private fun setupSyncStatus() {
        syncStatusManager = (application as dev.zihowl.dog.DogApplication).syncStatusManager
        syncStatusManager.syncStatus.observe(this) { status ->
            if (!sessionManager.isGuestMode) {
                val headerView = findViewById<NavigationView>(R.id.nav_view).getHeaderView(0)
                val headerSyncStatus = headerView.findViewById<TextView>(R.id.header_sync_status)
                headerSyncStatus.text = status
            }
            // Al recuperar el acceso al servidor se re-sincroniza el horario:
            // garantiza la entrega diferida de las notificaciones de cambios
            // ocurridos mientras el dispositivo estuvo sin conexión.
            val reconnected = status == dev.zihowl.dog.data.sync.SyncStatusManager.STATUS_SYNCED &&
                lastSyncStatus != null &&
                lastSyncStatus != dev.zihowl.dog.data.sync.SyncStatusManager.STATUS_SYNCED
            lastSyncStatus = status
            if (reconnected) {
                syncOfficialSchedule()
            }
        }
    }

    /** Último estado de sincronización observado (para detectar reconexión). */
    private var lastSyncStatus: String? = null

    /**
     * Listener registrado en [SyncStatusManager]: en cuanto Android reporta una
     * interfaz de red disponible se sincroniza el horario oficial de inmediato
     * (la petición GraphQL refresca el estado a "Sincronizado" al tener éxito)
     * y se fuerza la reconexión del WebSocket, sin esperar su ciclo de 5 s.
     */
    private val onNetworkAvailable: () -> Unit = {
        syncOfficialSchedule()
        realtimeClient?.reconnectNow()
    }

    private fun setupNetworkReconnectSync() {
        if (sessionManager.isGuestMode) return
        syncStatusManager.addOnNetworkAvailableListener(onNetworkAvailable)
    }

    private fun setupViewModels() {
        val repo = kotlinx.coroutines.runBlocking {
            (application as dev.zihowl.dog.DogApplication).repository()
        }
        repository = repo
        // Asegura que la UI solo vea los datos de la cuenta/sesión actual.
        repository.setActiveOwner(sessionManager.currentOwner())
        val factory = ViewModelFactory(repo)
        subjectsViewModel = ViewModelProvider(this, factory)[SubjectsViewModel::class.java]
        tasksViewModel = ViewModelProvider(this, factory)[TasksViewModel::class.java]
        notesViewModel = ViewModelProvider(this, factory)[NotesViewModel::class.java]
        scheduleViewModel = ViewModelProvider(this, factory)[ScheduleViewModel::class.java]

        subjectsViewModel.isSelectionMode.observe(this) { updateUiLockState() }
        tasksViewModel.isSelectionMode.observe(this) { updateUiLockState() }
        notesViewModel.isSelectionMode.observe(this) { updateUiLockState() }
    }

    private fun setupToolbarAndDrawer() {
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navigationView.setNavigationItemSelectedListener(this)
    }

    private fun setupViewPagerAndTabs(initialTab: Int = 0) {
        pagerAdapter = ViewPagerAdapter(this, sessionManager.role)
        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = 1

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pagerAdapter.tabs.getOrNull(position)?.title ?: ""
        }.attach()

        fun setTabBold(tab: TabLayout.Tab?, bold: Boolean) {
            val label = tab?.text?.toString() ?: return
            val spannable = SpannableString(label)
            if (bold) {
                spannable.setSpan(StyleSpan(Typeface.BOLD), 0, label.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            tab.text = spannable
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) = setTabBold(tab, true)
            override fun onTabUnselected(tab: TabLayout.Tab?) = setTabBold(tab, false)
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        tabLayout.post {
            for (i in 0 until tabLayout.tabCount) {
                val tab = tabLayout.getTabAt(i) ?: continue
                setTabBold(tab, i == tabLayout.selectedTabPosition)
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateTitleBasedOnPage(position)
                invalidateOptionsMenu()
            }
        })
        viewPager.setCurrentItem(initialTab, false)
        updateTitleBasedOnPage(initialTab)
    }

    fun showSubjectDetail(subjectName: String) {
        val fragment = SubjectDetailFragment.newInstance(subjectName)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(0, 0, 0, 0)
            .add(R.id.detail_fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun isCurrentTab(tabIndex: Int): Boolean = viewPager.currentItem == resolveLegacyTabIndex(tabIndex)

    fun isPerformanceVisible(): Boolean = fragmentContainer.visibility == View.VISIBLE

    private fun resolveLegacyTabIndex(legacy: Int): Int {
        val targetTitle = when (legacy) {
            0 -> "Materias"
            TAB_TASKS -> "Tareas"
            TAB_NOTES -> "Notas"
            TAB_SCHEDULE -> "Horario"
            else -> return if (::pagerAdapter.isInitialized) 0 else 0
        }
        if (!::pagerAdapter.isInitialized) {
            return if (sessionManager.role == SessionManager.ROLE_DOCENTE && targetTitle == "Tareas") 0
            else legacy.coerceAtLeast(0)
        }
        val idx = pagerAdapter.tabs.indexOfFirst { it.title == targetTitle }
        return if (idx >= 0) idx else 0
    }

    private fun updateTitleBasedOnPage(position: Int) {
        val newTitle = if (::pagerAdapter.isInitialized) {
            pagerAdapter.tabs.getOrNull(position)?.title ?: getString(R.string.app_name)
        } else getString(R.string.app_name)
        supportActionBar?.title = newTitle
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (fragmentContainer.visibility == View.VISIBLE) {
            hidePerformanceFragment()
        } else if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onBackStackChanged() {
        val isDetailVisible = supportFragmentManager.backStackEntryCount > 0
        contentMainView.visibility = if (isDetailVisible) View.GONE else View.VISIBLE
        setUiNavigationLock(isDetailVisible)
        invalidateOptionsMenu()
        if (!isDetailVisible) {
            updateTitleBasedOnPage(viewPager.currentItem)
        }
    }

    private fun updateUiLockState() {
        val isAnyFragmentInEditMode =
            subjectsViewModel.isSelectionMode.value == true ||
                tasksViewModel.isSelectionMode.value == true ||
                notesViewModel.isSelectionMode.value == true

        if (supportFragmentManager.backStackEntryCount == 0) {
            setUiNavigationLock(isAnyFragmentInEditMode)
        }
    }

    fun setUiNavigationLock(lock: Boolean) {
        viewPager.isUserInputEnabled = !lock
        val tabLayoutHeight = tabLayout.height
        val animationDuration = 0L

        tabLayout.animate()
            .translationY(if (lock) tabLayoutHeight.toFloat() else 0f)
            .setDuration(animationDuration)
            .start()

        if (lock) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            toggle.isDrawerIndicatorEnabled = false
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            toggle.setToolbarNavigationClickListener { onBackPressed() }
        } else {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            toggle.isDrawerIndicatorEnabled = true
            toggle.setToolbarNavigationClickListener(null)
            toggle.syncState()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val performanceVisible = fragmentContainer.visibility == View.VISIBLE
        menu?.findItem(R.id.action_add)?.isVisible =
            !performanceVisible && supportFragmentManager.backStackEntryCount == 0
        if (performanceVisible) {
            menu?.findItem(R.id.action_edit)?.isVisible = false
            menu?.findItem(R.id.action_delete)?.isVisible = false
            menu?.findItem(R.id.action_toggle_view)?.isVisible = false
            menu?.findItem(R.id.action_mark_not_completed)?.isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                when (pagerAdapter.tabs.getOrNull(viewPager.currentItem)?.title) {
                    "Materias" -> AddSubjectDialogFragment().show(supportFragmentManager, "AddSubjectDialog")
                    "Tareas" -> AddTaskDialogFragment.newInstance().show(supportFragmentManager, "AddTaskDialog")
                    "Notas" -> AddNoteDialogFragment().show(supportFragmentManager, "AddNoteDialog")
                    "Horario" -> dev.zihowl.dog.ui.schedule.AddManualEventDialogFragment().show(supportFragmentManager, "AddManualEventDialog")
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        when (item.itemId) {
            R.id.nav_performance -> {
                showPerformanceFragment()
                return true
            }
            R.id.nav_my_group -> {
                showScheduleSetupFragment()
                return true
            }
            R.id.nav_teacher_schedules -> {
                showTeacherSchedulesFragment()
                return true
            }
            R.id.nav_notifications -> {
                showNotificationsFragment()
                return true
            }
            R.id.nav_share_task -> {
                showSharedTasksFragment()
                return true
            }
        }
        return true
    }

    private fun showSharedTasksFragment() {
        showContainerFragment(
            "Tareas compartidas",
            dev.zihowl.dog.ui.sharedtasks.SharedTasksFragment::class.java
        )
    }

    private fun showTeacherSchedulesFragment() {
        showContainerFragment(
            "Horarios de profesores",
            dev.zihowl.dog.ui.teacherschedules.TeacherSchedulesFragment::class.java
        )
    }

    private fun showNotificationsFragment() {
        showContainerFragment(
            "Notificaciones",
            dev.zihowl.dog.ui.notifications.NotificationsFragment::class.java
        )
    }

    /**
     * Muestra un fragmento de pantalla completa en el contenedor compartido
     * (mismo mecanismo que Rendimiento y "Mi grupo y materias").
     */
    private fun showContainerFragment(title: String, fragmentClass: Class<out androidx.fragment.app.Fragment>) {
        contentMainView.visibility = View.GONE
        tabLayout.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE
        invalidateOptionsMenu()
        supportActionBar?.title = title

        val existing = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (existing == null || existing::class.java != fragmentClass) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragmentClass.getDeclaredConstructor().newInstance())
                .commit()
        }
    }

    private fun showScheduleSetupFragment() {
        contentMainView.visibility = View.GONE
        tabLayout.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE
        invalidateOptionsMenu()
        supportActionBar?.title = "Mi grupo y materias"

        val existing = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (existing !is ScheduleSetupFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ScheduleSetupFragment())
                .commit()
        }
    }

    private fun showPerformanceFragment() {
        contentMainView.visibility = View.GONE
        tabLayout.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE
        invalidateOptionsMenu()
        supportActionBar?.title = "Rendimiento Académico"

        val existing = supportFragmentManager.findFragmentById(R.id.fragment_container) as? PerformanceFragment
        if (existing == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PerformanceFragment())
                .commit()
        }
    }

    private fun hidePerformanceFragment() {
        contentMainView.visibility = View.VISIBLE
        tabLayout.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE
        invalidateOptionsMenu()
        updateTitleBasedOnPage(viewPager.currentItem)

        val existing = supportFragmentManager.findFragmentById(R.id.fragment_container)
        existing?.let {
            supportFragmentManager.beginTransaction()
                .remove(it)
                .commit()
        }
    }

    /**
     * Publica el grupo/subgrupo del alumno en el servidor para que la
     * colaboración pueda derivar quiénes son sus compañeros (RQNF-APP-43).
     */
    private fun publishAcademicProfile() {
        if (sessionManager.isGuestMode) return
        val baseUrl = sessionManager.serverBaseUrl ?: return
        val token = sessionManager.accessToken ?: return
        if (token.isBlank()) return
        lifecycleScope.launch {
            dev.zihowl.dog.data.remote.CollaborationClient().setAcademicProfile(
                baseUrl,
                token,
                sessionManager.selectedGroupId,
                sessionManager.selectedSubgroupId
            )
        }
    }

    private fun applyRoleVisibility() {
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val perf = navigationView.menu.findItem(R.id.nav_performance)
        perf?.isVisible = sessionManager.role != SessionManager.ROLE_DOCENTE

        // El docente no elige grupo: la app le importa su horario automáticamente.
        navigationView.menu.findItem(R.id.nav_my_group)?.let {
            it.isVisible = sessionManager.role != SessionManager.ROLE_DOCENTE
            // Requiere servidor: deshabilitado en modo invitado.
            it.isEnabled = !sessionManager.isGuestMode
        }

        if (sessionManager.isGuestMode) {
            navigationView.menu.findItem(R.id.nav_share_task)?.let {
                it.isVisible = true; it.isEnabled = false
            }
            navigationView.menu.findItem(R.id.nav_teacher_schedules)?.let {
                it.isVisible = true; it.isEnabled = false
            }
            navigationView.menu.findItem(R.id.nav_notifications)?.let {
                it.isVisible = true; it.isEnabled = false
            }
        } else {
            // Consulta de horarios de profesores: solo alumnos (RQF-APP-54/55).
            navigationView.menu.findItem(R.id.nav_teacher_schedules)?.let {
                it.isVisible = sessionManager.role != SessionManager.ROLE_DOCENTE
                it.isEnabled = true
            }
            // Notificaciones de cambios de horario: alumnos y docentes.
            navigationView.menu.findItem(R.id.nav_notifications)?.let {
                it.isVisible = true
                it.isEnabled = true
            }
            // Colaboración de tareas: alumnos y docentes con sesión (RQF-APP-45/46/47).
            navigationView.menu.findItem(R.id.nav_share_task)?.let {
                it.isVisible = true
                it.isEnabled = true
            }
        }
    }

    private fun setupAuthButtons() {
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val headerView = navigationView.getHeaderView(0)
        val headerUser = headerView.findViewById<TextView>(R.id.header_user)
        val headerUsername = headerView.findViewById<TextView>(R.id.header_username)
        val headerSyncStatus = headerView.findViewById<TextView>(R.id.header_sync_status)
        val headerServer = headerView.findViewById<TextView>(R.id.header_server)
        headerUser.text = sessionManager.username
        val handle = sessionManager.accountUsername
        if (!sessionManager.isGuestMode && !handle.isNullOrBlank()) {
            headerUsername.text = "@$handle"
            headerUsername.visibility = View.VISIBLE
        } else {
            headerUsername.visibility = View.GONE
        }

        val host = dev.zihowl.dog.utils.ServerUrlFormatter.displayHost(sessionManager.serverBaseUrl)
        if (sessionManager.isGuestMode || host.isEmpty()) {
            headerServer.text = getString(R.string.server_not_configured)
        } else {
            headerServer.text = getString(R.string.nav_connected_to, host)
        }

        val loginButton = navigationView.findViewById<View?>(R.id.nav_login_button)
        val logoutButton = navigationView.findViewById<View?>(R.id.nav_logout_button)

        if (sessionManager.isGuestMode) {
            headerSyncStatus.text = getString(R.string.sync_offline)
            headerSyncStatus.visibility = View.VISIBLE
            loginButton?.let { (it.parent as? View)?.visibility = View.VISIBLE }
            logoutButton?.let { (it.parent as? View)?.visibility = View.GONE }
        } else {
            headerSyncStatus.text = syncStatusManager.syncStatus.value ?: getString(R.string.sync_connected)
            updateAuthButtonVisibility()
        }

        loginButton?.setOnClickListener {
            // En modo invitado la sesión "existente" haría que
            // ServerConnectionActivity rebote de vuelta a MainActivity; se fuerza
            // mostrar la pantalla de configuración/login.
            navigateToServerConnection(forceConfig = true)
        }

        logoutButton?.setOnClickListener {
            performLogout()
        }
    }

    /**
     * Cierra sesión respaldando primero la información en el servidor. Los datos
     * locales solo se borran si el respaldo se confirmó; si falla, se avisa al
     * usuario y se le deja decidir.
     */
    private fun performLogout() {
        drawerLayout.closeDrawer(GravityCompat.START)
        val owner = sessionManager.currentOwner()
        val progress = androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage(R.string.backup_in_progress)
            .setCancelable(false)
            .create()
        progress.show()
        lifecycleScope.launch {
            val result = dev.zihowl.dog.data.backup.BackupManager(repository, sessionManager)
                .uploadBackup(owner)
            progress.dismiss()
            when (result) {
                is dev.zihowl.dog.data.backup.BackupManager.Result.Success,
                is dev.zihowl.dog.data.backup.BackupManager.Result.Empty ->
                    finalizeLogout(owner)
                is dev.zihowl.dog.data.backup.BackupManager.Result.Error ->
                    showBackupFailedDialog(owner)
            }
        }
    }

    private fun showBackupFailedDialog(owner: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.logout_backup_failed_title)
            .setMessage(R.string.logout_backup_failed_message)
            .setCancelable(false)
            .setPositiveButton(R.string.logout_retry) { _, _ -> performLogout() }
            .setNegativeButton(R.string.logout_force) { _, _ -> finalizeLogout(owner) }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun finalizeLogout(owner: String) {
        lifecycleScope.launch {
            repository.deleteByOwner(owner)
            // Limpia la sesión conservando el servidor configurado.
            sessionManager.isLoggedIn = false
            sessionManager.accessToken = null
            sessionManager.tokenExpiresAt = 0L
            sessionManager.accountKey = null
            sessionManager.backupKeyBase64 = null
            sessionManager.username = "Alumno"
            sessionManager.accountUsername = null
            sessionManager.selectedGroupId = -1
            sessionManager.selectedSubgroupId = -1
            sessionManager.scheduleConfigJson = null
            navigateToServerConnection()
        }
    }

    private fun navigateToServerConnection(forceConfig: Boolean = false) {
        val intent = Intent(this, dev.zihowl.dog.ui.serverconnection.ServerConnectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (forceConfig) {
            intent.putExtra(
                dev.zihowl.dog.ui.serverconnection.ServerConnectionActivity.EXTRA_FORCE_CONFIG,
                true
            )
        }
        startActivity(intent)
        finish()
    }

    private fun updateAuthButtonVisibility() {
        if (sessionManager.isGuestMode) return
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val loginButton = navigationView.findViewById<View?>(R.id.nav_login_button)
        val logoutButton = navigationView.findViewById<View?>(R.id.nav_logout_button)

        loginButton?.let { (it.parent as? View)?.visibility = if (sessionManager.isLoggedIn) View.GONE else View.VISIBLE }
        logoutButton?.let { (it.parent as? View)?.visibility = if (sessionManager.isLoggedIn) View.VISIBLE else View.GONE }
    }

    override fun onDestroy() {
        super.onDestroy()
        // syncStatusManager es un singleton de proceso: su NetworkCallback no
        // se desregistra aquí, vive mientras viva el proceso. El listener de
        // esta Activity sí se quita para no fugar la referencia.
        if (::syncStatusManager.isInitialized) {
            syncStatusManager.removeOnNetworkAvailableListener(onNetworkAvailable)
        }
        realtimeClient?.stop()
        realtimeClient = null
    }
}
