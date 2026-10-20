package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 520 to 530: Dionysius Exiguus's Easter table of 525, which counted its years from the Incarnation of Christ. */
val db152Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 152,
        kind = Kind.TOOL,
        section = "Calendar",
        title = "Dionysius Exiguus's Easter table of 525, which counted its years from the Incarnation of Christ",
        originalTitle = "Tabula paschalis",
        author = "Dionysius Exiguus",
        year = 525,
        summary = "Dionysius Exiguus's Easter table of 525 numbered its years from the Incarnation of Christ, and that count later became the AD era.",
        details = "He began the table with AD 532, the year 248 of Diocletian, and his covering letter dates the work by the consulship of Probus Junior in 525. The new count spread slowly and came into wide use only in the ninth century.",
    ),

)
