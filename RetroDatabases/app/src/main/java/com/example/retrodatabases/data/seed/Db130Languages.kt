package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 740 to 750: the Latin Chronicle of 741, written in al-Andalus and favourable to the Umayyads. */
val db130Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 130,
        kind = Kind.LANGUAGE,
        section = "Iberian",
        title = "The Chronicle of 741, a Latin history written in al-Andalus that leans towards the Umayyads",
        originalTitle = "Chronica Byzantina-Arabica",
        author = "Anonymous",
        year = 741,
        summary = "The Chronicle of 741 is a Latin history written in al-Andalus in about 741 to 743, and it leans towards the Umayyad rulers.",
        details = "Its usual name comes from the year 741, which is close to the time of writing, and a modern archive also lists it under the title Chronica Byzantina-Arabica. The author is unknown, and the sources date the text to 741 to 743, so 741 is the year used here.",
        rare = true,
    ),

)
