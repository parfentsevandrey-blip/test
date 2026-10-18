package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1080 to 1090: the astronomical tables made at Toledo under al-Zarqali. */
val db96Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 96,
        kind = Kind.TOOL,
        section = "Iberia",
        title = "The Toledan Tables of al-Zarqali, astronomical tables made at Toledo about 1080",
        originalTitle = "Tabulae Toletanae",
        author = "al-Zarqali and Ibn Sa'id",
        year = 1080,
        summary = "The Toledan Tables are astronomical tables made at Toledo by a team that included al-Zarqali, and they are usually dated to about 1080.",
        details = "Accounts differ on who led the team, with Ibn Sa'id named as director in some and al-Zarqali as author of the final text in others, and they differ on whether earlier observation tables belong to the same work. In one account al-Zarqali left Toledo between 1081 and 1085, the later date being the year Alfonso VI took the city.",
        rare = true,
    ),

)
