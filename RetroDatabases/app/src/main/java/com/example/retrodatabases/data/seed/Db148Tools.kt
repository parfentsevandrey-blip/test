package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 560 to 570: Cassiodorus's handbook for the monks of Vivarium, placed at 562, a year that some accounts give for his death. */
val db148Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 148,
        kind = Kind.TOOL,
        section = "Latin",
        title = "Cassiodorus's Institutions, a handbook of divine and secular learning for the monks of Vivarium",
        originalTitle = "Institutiones divinarum et saecularium litterarum",
        author = "Cassiodorus",
        year = 562,
        summary = "Cassiodorus wrote this handbook for the monks of his monastery at Vivarium, setting out the study of scripture and of secular letters.",
        details = "The handbook was drafted over the 530s to 550s and revised to the end of Cassiodorus's life, so the year used here is not its date. Accounts of his death differ, with dates between about 562 and about 585, and 562 is used only as the anchor for this placed record.",
        rare = true,
    ),

)
