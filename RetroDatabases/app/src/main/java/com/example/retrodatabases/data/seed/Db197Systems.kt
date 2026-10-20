package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One system of 70 to 80: the census that Vespasian and Titus held as censors, dated 73 to 74. */
val db197Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 197,
        kind = Kind.SYSTEM,
        section = "Census",
        title = "The census of Vespasian and Titus as censors, dated 73 to 74",
        originalTitle = "Census",
        author = "Vespasian and Titus",
        year = 74,
        summary = "Vespasian and his son Titus held the census as censors, a count of the citizens and their property that the usual reckoning dates to 73 and 74.",
        details = "A Roman censor kept the register of citizens and their property, so the census was a counting system in the strict sense, and Titus took part as co-censor with his father. The record uses 74 as its year, the later of the two years usually given for the count.",
        rare = true,
    ),

)
