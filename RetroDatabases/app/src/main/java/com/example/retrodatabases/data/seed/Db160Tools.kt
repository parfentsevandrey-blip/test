package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 440 to 450: He Chengtian's Yuanjia calendar, proposed in 443 for the Liu Song court. */
val db160Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 160,
        kind = Kind.TOOL,
        section = "Calendars",
        title = "He Chengtian's Yuanjia calendar, proposed in 443 to start each month at the true conjunction of the Moon",
        originalTitle = "Yuanjia li",
        author = "He Chengtian",
        year = 443,
        summary = "He Chengtian's Yuanjia calendar, proposed for the Liu Song court in 443, aimed to start each month at the true conjunction of the Moon and the Sun, allowing for the Moon's irregular speed.",
        details = "He Chengtian (370 to 447) proposed the calendar, and the court's director and deputy objected that it was too intricate, though they granted that it was accurate. Accounts differ on the year it was first used, with some giving 445 as the start of its use and others counting from the proposal of 443.",
        rare = true,
    ),

)
