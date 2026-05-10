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
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dev.zihowl.dog.R
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.data.sync.SyncStatusManager
import dev.zihowl.dog.ui.ViewModelFactory
import dev.zihowl.dog.ui.notes.AddNoteDialogFragment
import dev.zihowl.dog.ui.notes.NotesViewModel
import dev.zihowl.dog.ui.subjects.AddSubjectDialogFragment
import dev.zihowl.dog.ui.subjects.SubjectsViewModel
import dev.zihowl.dog.ui.subjects.detail.SubjectDetailFragment
import dev.zihowl.dog.ui.schedule.ScheduleViewModel
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

    private lateinit var subjectsViewModel: SubjectsViewModel
    private lateinit var tasksViewModel: TasksViewModel
    private lateinit var notesViewModel: NotesViewModel
    private lateinit var scheduleViewModel: ScheduleViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var syncStatusManager: SyncStatusManager

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

        val initialTab = intent?.getIntExtra(EXTRA_OPEN_TAB, -1)?.takeIf { it in 0..3 } ?: 0
        intent?.removeExtra(EXTRA_OPEN_TAB)

        setupViewModels()
        setupToolbarAndDrawer()
        setupSyncStatus()
        setupAuthButtons()
        setupViewPagerAndTabs(initialTab)

        supportFragmentManager.addOnBackStackChangedListener(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getIntExtra(EXTRA_OPEN_TAB, -1)
        if (tab in 0..3) {
            viewPager.setCurrentItem(tab, false)
            updateTitleBasedOnPage(tab)
            intent.removeExtra(EXTRA_OPEN_TAB)
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "dev.zihowl.dog.extra.OPEN_TAB"
        const val TAB_TASKS = 1
        const val TAB_NOTES = 2
        const val TAB_SCHEDULE = 3
    }

    private fun setupSyncStatus() {
        syncStatusManager = SyncStatusManager(this)
        syncStatusManager.syncStatus.observe(this) { status ->
            if (!sessionManager.isGuestMode) {
                val headerView = findViewById<NavigationView>(R.id.nav_view).getHeaderView(0)
                val headerSyncStatus = headerView.findViewById<TextView>(R.id.header_sync_status)
                headerSyncStatus.text = status
            }
        }
    }

    private fun setupViewModels() {
        val repo = kotlinx.coroutines.runBlocking {
            (application as dev.zihowl.dog.DogApplication).repository()
        }
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
        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 1

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Materias"
                1 -> "Tareas"
                2 -> "Notas"
                3 -> "Horario"
                else -> ""
            }
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

    fun isCurrentTab(tabIndex: Int): Boolean = viewPager.currentItem == tabIndex

    private fun updateTitleBasedOnPage(position: Int) {
        val newTitle = when (position) {
            0 -> "Materias"
            1 -> "Tareas"
            2 -> "Notas"
            3 -> "Horario"
            else -> getString(R.string.app_name)
        }
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
        menu?.findItem(R.id.action_add)?.isVisible =
            fragmentContainer.visibility != View.VISIBLE &&
                supportFragmentManager.backStackEntryCount == 0
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                when (viewPager.currentItem) {
                    0 -> AddSubjectDialogFragment().show(supportFragmentManager, "AddSubjectDialog")
                    1 -> AddTaskDialogFragment.newInstance().show(supportFragmentManager, "AddTaskDialog")
                    2 -> AddNoteDialogFragment().show(supportFragmentManager, "AddNoteDialog")
                    3 -> dev.zihowl.dog.ui.schedule.AddManualEventDialogFragment().show(supportFragmentManager, "AddManualEventDialog")
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
        }
        return true
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

    private fun setupAuthButtons() {
        sessionManager = SessionManager(this)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val headerView = navigationView.getHeaderView(0)
        val headerUser = headerView.findViewById<TextView>(R.id.header_user)
        val headerSyncStatus = headerView.findViewById<TextView>(R.id.header_sync_status)
        headerUser.text = sessionManager.username

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
            navigateToServerConnection()
        }

        logoutButton?.setOnClickListener {
            navigateToServerConnection()
        }
    }

    private fun navigateToServerConnection() {
        val intent = Intent(this, dev.zihowl.dog.ui.serverconnection.ServerConnectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
        if (::syncStatusManager.isInitialized) {
            syncStatusManager.unregister()
        }
    }
}
