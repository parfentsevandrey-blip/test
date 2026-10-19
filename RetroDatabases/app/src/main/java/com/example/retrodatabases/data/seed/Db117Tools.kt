package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 870 to 880: the Procheiros Nomos, a Greek law handbook issued under Basil I, dated 879 within a disputed range. */
val db117Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 117,
        kind = Kind.TOOL,
        section = "Byzantium",
        title = "The Procheiros Nomos, a short Greek law handbook issued under Basil I",
        originalTitle = "Procheiros Nomos",
        author = "Basil I and his jurists",
        year = 879,
        summary = "The Procheiros Nomos, a short Greek law handbook meant for everyday use, was issued under the emperor Basil I, and its date is disputed.",
        details = "Accounts differ on its date: one puts its issue between 867 and 879 under Basil I, and another gives 907, when it was adopted under Leo VI. The year 879 used here is the end of the earlier range, so it marks a date that is still open rather than a settled one.",
        rare = true,
    ),

)
