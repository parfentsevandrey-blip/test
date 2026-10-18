package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 970 to 976: the al-Azhar mosque at Cairo, and the land system of Goryeo Korea. */
val db107Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 107,
        kind = Kind.SYSTEM,
        section = "Egypt",
        title = "The al-Azhar mosque at Cairo, begun on 4 April 970 under Jawhar al-Siqilli and opened in 972",
        originalTitle = "al-Jami' al-Azhar",
        author = "Jawhar al-Siqilli for the Fatimid caliph al-Mu'izz",
        year = 970,
        summary = "The Fatimid general Jawhar al-Siqilli began the al-Azhar mosque at Cairo on 4 April 970, and its first Friday congregation was held in Ramadan 972.",
        details = "Accounts give the building time as two years and three months, which is 27 months, while one gives about three years. Its teaching role is dated to 988 in one account and to 989 in another, when 35 scholars were brought on, so the teaching came well after the foundation.",
    ),

    entry(
        databaseId = 107,
        kind = Kind.SYSTEM,
        section = "Goryeo Korea",
        title = "The Jeonsi-gwa, the land system of Goryeo Korea that paid officials with fields and firewood land, set up in 976",
        originalTitle = "Jeonsi-gwa",
        author = "King Gyeongjong of Goryeo",
        year = 976,
        summary = "King Gyeongjong of Goryeo set up the Jeonsi-gwa in 976, a system that gave officials grants of fields and firewood land graded by rank.",
        details = "Officials held the grants while they served and drew the rent from them, so the land stood in for a salary. Pay followed office in this scheme, and the year 976 is the one usually given for its setting up.",
        rare = true,
    ),

)
