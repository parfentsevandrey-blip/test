package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 80 to 84: Wang Chong's Lunheng of critical essays and Frontinus's Strategemata, a Latin book of military stratagems. */
val db196Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 196,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Frontinus's Strategemata, a Latin book of military stratagems",
        originalTitle = "Strategemata",
        author = "Sextus Julius Frontinus",
        year = 84,
        summary = "Frontinus's Strategemata, a Latin handbook of military stratagems, was written in the last decades of the first century and is set out in four books.",
        details = "Three of the four books are undoubtedly Frontinus's, and the authenticity of the fourth has been challenged. The date of the work is not fixed beyond the last decades of the first century, so 84 is used here as the year of the book.",
        rare = true,
    ),

    entry(
        databaseId = 196,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Wang Chong's Lunheng, critical essays that test the claims of his age",
        originalTitle = "Lunheng",
        author = "Wang Chong",
        year = 80,
        summary = "Wang Chong's Lunheng, a Chinese collection of critical essays, tests the omens, legends and claims of its age against observed fact, and its first publication is dated about 80.",
        details = "Wang Chong lived in the first century, from about 27 to about 97, and he wrote the essays in the Han. One analysis of the internal evidence narrows the composition of the work to the years 76 to 84, so the year 80 is a round date and not a fixed one.",
        rare = true,
    ),

)
