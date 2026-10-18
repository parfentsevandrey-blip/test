package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1100 to 1108: a coronation charter, a crusader kingdom, a double monastery, a Song school system and an abbey of canons at Paris. */
val db94Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 94,
        kind = Kind.SYSTEM,
        section = "England",
        title = "Henry I's Coronation Charter, a charter of liberties of 1100",
        originalTitle = "Henry I's Coronation Charter, a charter of liberties of 1100",
        author = "Henry I of England",
        year = 1100,
        summary = "Henry I issued a charter at his coronation on 5 August 1100 that promised good government, restored the laws of Edward the Confessor and limited the feudal payments owed to the crown.",
        details = "The charter names the abuses of the late king's rule, such as heavy feudal fees and the sale or leasing of church offices, and it promises to end them. The text is known from later copies, and historians debate how far Henry kept these promises.",
    ),

    entry(
        databaseId = 94,
        kind = Kind.SYSTEM,
        section = "Crusader states",
        title = "The Latin kingdom of Jerusalem, crowned at Bethlehem under Baldwin I",
        originalTitle = "Regnum Hierosolymitanum",
        author = "Godfrey of Bouillon and Baldwin I",
        year = 1100,
        summary = "The Latin kingdom of Jerusalem took its first crowned king in 1100, when Baldwin I was crowned at Bethlehem at Christmas after the death of his brother Godfrey.",
        details = "Godfrey had held the title of Advocate of the Holy Sepulchre and died on 18 July 1100, after which Baldwin took the royal title. The coronation is dated to Christmas Day 1100, and the kingdom was then governed by a court of the crusader lords.",
    ),

    entry(
        databaseId = 94,
        kind = Kind.SYSTEM,
        section = "Monasticism",
        title = "Fontevraud, a double monastery for monks and nuns under an abbess",
        originalTitle = "Fontevraud",
        author = "Robert of Arbrissel",
        year = 1101,
        summary = "Fontevraud Abbey was founded in 1101 by Robert of Arbrissel as a double monastery where monks and nuns lived under the rule of an abbess.",
        details = "The abbey lies in the Loire valley near Saumur, and Robert of Arbrissel had preached an austere religious life in western France before founding it. Few other new religious houses of the period placed a woman at the head of both communities, which made Fontevraud unusual.",
    ),

    entry(
        databaseId = 94,
        kind = Kind.SYSTEM,
        section = "Schools",
        title = "The Three Halls of Song schools, with promotion by tests",
        originalTitle = "Sanshe fa",
        author = "Cai Jing and the court of Emperor Huizong",
        year = 1102,
        summary = "The Three Halls system moved Song school pupils through outer, inner and upper halls by regular tests, and in 1102 Cai Jing carried the capital's model out to prefectural schools.",
        details = "Pupils sat tests each month, season and year, and the best advanced toward the Imperial University in the capital. One study dates the extension to 1102, while the first examinations linked to the reform are dated to 1104, so accounts differ on the start; the system was dropped in 1121.",
        rare = true,
    ),

    entry(
        databaseId = 94,
        kind = Kind.SYSTEM,
        section = "Paris",
        title = "The abbey of Saint-Victor in Paris, a house of canons founded in 1108",
        originalTitle = "Abbatia Sancti Victoris",
        author = "William of Champeaux",
        year = 1108,
        summary = "The abbey of Saint-Victor was founded outside Paris in 1108 by William of Champeaux as a house of Augustinian canons who lived by a common rule.",
        details = "William of Champeaux had taught at the cathedral school of Paris before he founded the community. The abbey later became known for its learned canons and its teaching, and 1108 is the usual founding date.",
    ),

)
