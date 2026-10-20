package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of the 450s: Victorius of Aquitaine's Easter table of 457, a 532-year table from AD 28 to AD 559. */
val db159Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 159,
        kind = Kind.TOOL,
        section = "Calendar",
        title = "Victorius of Aquitaine's Easter table of 457, a 532-year table from AD 28 to AD 559",
        originalTitle = "Cursus paschalis",
        author = "Victorius of Aquitaine",
        year = 457,
        summary = "Victorius of Aquitaine's Easter table of 457 gives the dates of Easter across a 532-year span that runs from AD 28 to AD 559.",
        details = "Victorius numbered his years from AD 28, the year he assigned to the Passion, and he drew his consular years from the consul list in Prosper's chronicle. Gaul and Spain kept using his tables until Dionysian tables replaced them in the late 700s. Accounts differ on how many of the table's errors were his own and how many came from later copies.",
        rare = true,
    ),

)
