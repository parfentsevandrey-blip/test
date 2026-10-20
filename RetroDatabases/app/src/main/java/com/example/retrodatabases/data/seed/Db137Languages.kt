package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One language record of 670 to 680: the Kentish laws of Hlothhere and Eadric, written in Old English and placed at 673. */
val db137Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 137,
        kind = Kind.LANGUAGE,
        section = "Old English",
        title = "The laws of Hlothhere and Eadric of Kent, the second English law code, written in Old English",
        originalTitle = "Hlothhere and Eadric",
        author = "Hlothhere and Eadric, kings of Kent",
        year = 673,
        summary = "The laws of Hlothhere and Eadric, kings of Kent, are the second surviving English law code after the laws of Aethelberht and are written in Old English.",
        details = "The text does not give its own date, and scholars assign it to the reigns of Hlothhere and Eadric, who ruled Kent from 673 to 686. The year 673, when Hlothhere began to reign, is the placed year of this record, the start of a documented reign, and not a date given by the code.",
        rare = true,
    ),

)
