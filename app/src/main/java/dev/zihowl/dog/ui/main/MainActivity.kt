package dev.zihowl.dog.ui.main

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import dev.zihowl.dog.ui.ViewModelFactory
import dev.zihowl.dog.ui.notes.AddNoteDialogFragment
import dev.zihowl.dog.ui.notes.NotesViewModel
import dev.zihowl.dog.ui.subjects.AddSubjectDialogFragment
import dev.zihowl.dog.ui.subjects.SubjectsViewModel
import dev.zihowl.dog.ui.subjects.detail.SubjectDetailFragment
import dev.zihowl.dog.ui.schedule.ScheduleViewModel
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

    private lateinit var subjectsViewModel: SubjectsViewModel
    private lateinit var tasksViewModel: TasksViewModel
    private lateinit var notesViewModel: NotesViewModel
    private lateinit var scheduleViewModel: ScheduleViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        drawerLayout = findViewById(R.id.drawer_layout)
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        contentMainView = findViewById(R.id.contentMainLayout)

        setupViewModels()
        setupToolbarAndDrawer()
        setupViewPagerAndTabs()

        supportFragmentManager.addOnBackStackChangedListener(this)
    }

    private fun setupViewModels() {
        val factory = ViewModelFactory((application as dev.zihowl.dog.DogApplication).repository)
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

    private fun setupViewPagerAndTabs() {
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
        viewPager.currentItem = 0
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                when (viewPager.currentItem) {
                    0 -> AddSubjectDialogFragment().show(supportFragmentManager, "AddSubjectDialog")
                    1 -> AddTaskDialogFragment.newInstance().show(supportFragmentManager, "AddTaskDialog")
                    2 -> AddNoteDialogFragment().show(supportFragmentManager, "AddNoteDialog")
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
