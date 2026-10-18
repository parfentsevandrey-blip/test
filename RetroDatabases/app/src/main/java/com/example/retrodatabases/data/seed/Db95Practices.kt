package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1095: al-Ghazali's withdrawal from teaching into a Sufi retreat that lasted about ten years. */
val db95Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 95,
        kind = Kind.PRACTICE,
        section = "Sufism",
        title = "Al-Ghazali's withdrawal from teaching into a Sufi retreat of about ten years",
        originalTitle = "Uzla",
        author = "Abu Hamid al-Ghazali",
        year = 1095,
        summary = "In 1095 al-Ghazali left his teaching post in Baghdad and withdrew from public teaching, spending about ten years in Sufi retreat, mostly in Syria, Jerusalem and the Hijaz.",
        details = "He describes the crisis and the withdrawal in his autobiography, written after he resumed teaching. Historians rely on that text for the dates of his journeys, so the travel dates are his own account.",
        rare = true,
    ),

)
