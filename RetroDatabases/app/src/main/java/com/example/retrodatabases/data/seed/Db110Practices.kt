package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Ibn Muqla's proportions for the six Arabic scripts, placed at 940, the year of his death, since no dated practice of the decade is secured. */
val db110Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 110,
        kind = Kind.PRACTICE,
        section = "Calligraphy",
        title = "Ibn Muqla's rule for the six Arabic scripts, measured by the alif and the diamond dot",
        originalTitle = "Al-aqlam al-sitta",
        author = "Ibn Muqla",
        year = 940,
        summary = "Ibn Muqla set out the proportions of the six standard Arabic scripts by measuring each letter against the upright alif, and the record is placed at 940, the year of his death.",
        details = "The sources do not date the rule itself, so 940 is the placed year: it is the year of Ibn Muqla's death, and not the year the system was devised. The system used a circle sized to the alif and a rhombus-shaped dot cut from the pen's nib. Later calligraphers, Ibn al-Bawwab and Yaqut al-Musta'simi, built on it, and the lists of the six scripts differ on one name, tawqi' or tumar.",
        rare = true,
    ),

)
