package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 980 to 990: the Peace of God, a truce sworn to by lords and proclaimed in western France. */
val db106Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 106,
        kind = Kind.PRACTICE,
        section = "France",
        title = "The Peace of God, oath-backed truces proclaimed at Charroux in 989",
        originalTitle = "Pax Dei",
        author = "Councils of western France",
        year = 989,
        summary = "The Peace of God, first proclaimed at a council held at Charroux in 989, asked lords and their followers to swear protection for churches, clergy and the unarmed.",
        details = "Later councils extended the peace with oaths, penalties for breaking it and set days of truce, and the practice spread through the west of France and beyond. Its oaths show how churchmen tried to bind armed men to rules of conduct.",
    ),

)
