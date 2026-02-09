package com.purgatory.tasks

import androidx.annotation.ColorRes

data class AppUser(
    val id: String,
    val displayName: String,
    @ColorRes val colorRes: Int
)

object AppUsers {
    val all = listOf(
        AppUser("neil", "Neil", R.color.owner_neil),
        AppUser("jean", "Jean", R.color.owner_jean),
        AppUser("both", "Both", R.color.owner_both)
    )

    fun byDisplayName(name: String?): AppUser? =
        all.firstOrNull { it.displayName.equals(name, ignoreCase = true) }

    fun byId(id: String?): AppUser? =
        all.firstOrNull { it.id.equals(id, ignoreCase = true) }
}
