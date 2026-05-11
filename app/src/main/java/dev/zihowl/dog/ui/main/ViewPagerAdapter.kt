package dev.zihowl.dog.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.ui.notes.NotesFragment
import dev.zihowl.dog.ui.schedule.ScheduleFragment
import dev.zihowl.dog.ui.subjects.SubjectsFragment
import dev.zihowl.dog.ui.tasks.TasksFragment

class ViewPagerAdapter(
    fragmentActivity: FragmentActivity,
    role: String = SessionManager.ROLE_ALUMNO
) : FragmentStateAdapter(fragmentActivity) {

    data class TabSpec(val title: String, val factory: () -> Fragment)

    val tabs: List<TabSpec> = if (role == SessionManager.ROLE_DOCENTE) {
        listOf(
            TabSpec("Materias") { SubjectsFragment() },
            TabSpec("Notas") { NotesFragment() },
            TabSpec("Horario") { ScheduleFragment() }
        )
    } else {
        listOf(
            TabSpec("Materias") { SubjectsFragment() },
            TabSpec("Tareas") { TasksFragment() },
            TabSpec("Notas") { NotesFragment() },
            TabSpec("Horario") { ScheduleFragment() }
        )
    }

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment = tabs[position].factory()
}
