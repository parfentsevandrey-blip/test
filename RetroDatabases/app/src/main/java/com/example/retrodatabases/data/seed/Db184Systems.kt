package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 200 to 210: the Manusmriti, the Laws of Manu, a Sanskrit law book in verse whose year is approximate. */
val db184Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 184,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Manusmriti, the Laws of Manu, a Sanskrit law book in verse",
        originalTitle = "Manava Dharmasastra",
        author = "Attributed to Manu, with unknown authors",
        year = 200,
        summary = "The Manusmriti is a Sanskrit law book in verse that sets out duties, social order and penance for the Hindu tradition.",
        details = "Accounts date the text between the second century BCE and the second or third century CE, so 200 marks the approximate end of the usual window and is not a date of composition. Its authors are unknown, and it is one of the best-known Dharmasastra law books.",
    ),

)
