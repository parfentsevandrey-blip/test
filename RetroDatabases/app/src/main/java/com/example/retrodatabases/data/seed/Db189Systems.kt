package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 150 to 160: the Coptic alphabet, an undated writing system placed at 155 with the anchor of Justin Martyr's First Apology. */
val db189Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 189,
        kind = Kind.SYSTEM,
        section = "Writing",
        title = "The Coptic alphabet, Greek letters with Demotic signs for the sounds Greek lacked",
        originalTitle = "Coptic script",
        author = "Scribes of Roman Egypt",
        year = 155,
        summary = "The Coptic alphabet joined Greek letters with a few signs taken from Demotic to write the sounds of Egyptian, and its invention is undated in the sources.",
        details = "The sources do not give one date for the invention of the script, and accounts differ between the late fourth century BCE and the second century CE. The year 155 is the placed year, the date of the anchor, Justin Martyr's First Apology, which is dated about 155, and it is not the date of the script.",
        rare = true,
    ),

)
