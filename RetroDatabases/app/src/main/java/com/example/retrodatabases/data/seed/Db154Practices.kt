package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 500 to 510: compurgation, the oath backed by oath-helpers, placed at 501, the usual start of the Burgundian code. */
val db154Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 154,
        kind = Kind.PRACTICE,
        section = "Germanic",
        title = "Compurgation, the oath of an accused man backed by oath-helpers, as the early Germanic law codes describe it",
        originalTitle = "conjuratores",
        author = "Germanic law codes of the early sixth century",
        year = 501,
        summary = "In Germanic courts an accused man could clear himself by an oath backed by oath-helpers, a practice that the early sixth-century law codes describe.",
        details = "The sources do not date this practice to a year of its own, because the codes describe an older custom. The Burgundian code issued under Gundobad, usually dated to about 501 to 516, is used here as the anchor, and 501 is the anchor for the record and not the date of the practice.",
        rare = true,
    ),

)
