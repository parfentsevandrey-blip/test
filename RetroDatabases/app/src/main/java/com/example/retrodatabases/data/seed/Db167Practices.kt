package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 370: Basil of Caesarea's Longer Rules, placed at the year Basil became bishop because the fuller rules are undated. */
val db167Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 167,
        kind = Kind.PRACTICE,
        section = "Monastic",
        title = "Basil of Caesarea's Longer Rules, a monastic code in questions and answers",
        originalTitle = "Regulae fusius tractatae",
        author = "Basil of Caesarea",
        year = 370,
        summary = "Basil of Caesarea set out a code of monastic life in questions and answers, and the Longer Rules grew in stages over his years as a leader of monks.",
        details = "The sources do not date the fuller form of these rules, and accounts differ on whether they grew from a shorter first edition or were finished at Caesarea. The year 370 is the year Basil became bishop of Caesarea, the anchor placed here, not the date of the rules.",
        rare = true,
    ),

)
