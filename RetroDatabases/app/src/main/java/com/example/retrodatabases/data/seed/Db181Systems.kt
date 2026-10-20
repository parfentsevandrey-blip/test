package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 230 to 240: the Jingchu calendar of Wei, adopted in 237 and credited to Yang Wei. */
val db181Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 181,
        kind = Kind.SYSTEM,
        section = "Calendars",
        title = "The Jingchu calendar of Wei, the Luminous Inception system adopted in 237",
        originalTitle = "Jingchu li",
        author = "Yang Wei",
        year = 237,
        summary = "The Jingchu calendar, the Luminous Inception system, was adopted by the Wei state in 237 and later served as the state calendar of the Jin.",
        details = "The sources date its adoption to 237, and a modern study credits Yang Wei with the system, which is known in English as the Luminous Inception system. Later accounts describe it as the state calendar of Wei and then of the Jin, and 237 is the year of adoption used here rather than the date of any decree.",
        rare = true,
    ),

)
