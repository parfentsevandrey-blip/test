package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 50 to 59: a string of counting beads, placed at 51 by its anchor because the sources do not date its first use. */
val db199Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 199,
        kind = Kind.TOOL,
        section = "Counting",
        title = "A string of counting beads, moved one by one to count recitations",
        originalTitle = "Japamala",
        author = "Hindu and Buddhist devotees",
        year = 51,
        summary = "A string of beads, often 108 in number, is moved along by the fingers so that the wearer can count each recitation without keeping the count in the head.",
        details = "The sources do not date the first use of the counting string, so 51 is the year of the anchor, the capture of Caratacus, and not the year of its making. The count passes from one bead to the next as each recitation is made, a counting method that needs no written record.",
        rare = true,
    ),

)
