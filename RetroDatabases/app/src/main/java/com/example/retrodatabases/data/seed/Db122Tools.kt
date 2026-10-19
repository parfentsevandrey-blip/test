package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 820 to 830: al-Khwarizmi's Algebra, the Arabic book of restoring and balancing for solving linear and quadratic equations. */
val db122Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 122,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Al-Khwarizmi's Algebra, the Compendious Book on Calculation by Completion and Balancing",
        originalTitle = "Kitab al-mukhtasar fi hisab al-jabr wa'l-muqabala",
        author = "Muhammad ibn Musa al-Khwarizmi",
        year = 820,
        summary = "Al-Khwarizmi's Algebra, written in Baghdad about 820, gives a systematic method of restoring and balancing for solving linear and quadratic equations.",
        details = "The book sorts its equations into six kinds and works each through with worked examples in words rather than symbols. The date of about 820 is the usual one, and this record is the Arabic book itself, since Robert of Chester's Latin translation, finished in 1145, is a separate work.",
    ),

)
