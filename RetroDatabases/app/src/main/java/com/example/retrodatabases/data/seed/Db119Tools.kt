package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 850 to 860: Mahavira's Ganita-sara-sangraha, an arithmetic handbook written in the time of the Rashtrakuta king Amoghavarsha I. */
val db119Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 119,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Mahavira's Ganita-sara-sangraha, an arithmetic handbook from the court of Amoghavarsha I",
        originalTitle = "Ganita-sara-sangraha",
        author = "Mahavira, a Jain mathematician",
        year = 850,
        summary = "Mahavira's Ganita-sara-sangraha is a Sanskrit handbook of arithmetic rules and worked problems, written in the time of the Rashtrakuta king Amoghavarsha I and dated about 850.",
        details = "Mahavira is listed among the scholars who worked under Amoghavarsha I, and the handbook is usually dated about 850. It is a teaching book of rules with worked examples for practical reckoning.",
        rare = true,
    ),

)
