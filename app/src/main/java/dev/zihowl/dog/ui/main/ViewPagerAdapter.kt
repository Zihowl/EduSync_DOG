package dev.zihowl.dog.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import dev.zihowl.dog.ui.notes.NotesFragment
import dev.zihowl.dog.ui.schedule.ScheduleFragment
import dev.zihowl.dog.ui.subjects.SubjectsFragment
import dev.zihowl.dog.ui.tasks.TasksFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SubjectsFragment()
            1 -> TasksFragment()
            2 -> NotesFragment()
            3 -> ScheduleFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
