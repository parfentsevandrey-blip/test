package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1380 to 1390: a school feeding Oxford, a Portuguese assembly, three German and English foundations and a parliament of 1388. */
val db66Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 66,
        kind = Kind.SYSTEM,
        section = "Schools",
        title = "Winchester College, founded by William of Wykeham in 1382",
        originalTitle = "College of Saint Mary of Winchester",
        author = "William of Wykeham",
        year = 1382,
        summary = "William of Wykeham founded Winchester College in 1382 as a school whose scholars passed on to his college at Oxford, New College.",
        details = "The college took seventy poor scholars and taught them before they went on to New College, which Wykeham had founded in 1379. The date 1382 is the foundation year usually given for the school.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.SYSTEM,
        section = "Parliaments",
        title = "The Cortes of Coimbra, which made John of Aviz king of Portugal in 1385",
        originalTitle = "Cortes de Coimbra",
        author = "The estates of the kingdom of Portugal",
        year = 1385,
        summary = "The Cortes of Coimbra of 1385 proclaimed John, Master of Aviz, king of Portugal after the succession crisis of 1383 to 1385.",
        details = "The assembly met at Coimbra in the spring of 1385 and chose John as king. The battle of Aljubarrota, fought in August 1385, confirmed his claim.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Heidelberg, opened in 1386",
        originalTitle = "Universitas Heidelbergensis",
        author = "Pope Urban VI and the Elector Palatine Ruprecht I",
        year = 1386,
        summary = "The University of Heidelberg opened in 1386 under a papal bull of Urban VI and a charter of the Elector Palatine Ruprecht I.",
        details = "Accounts differ on the founding year, since the bull is usually dated to 1385 and the opening to 1386. Marsilius of Inghen, a Dutch master of arts from Paris, was its first rector.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Cologne, founded in 1388",
        originalTitle = "Studium Generale Coloniense",
        author = "Pope Urban VI and the city council of Cologne",
        year = 1388,
        summary = "The University of Cologne was founded in 1388 under a papal bull of Urban VI, with the backing of the city council.",
        details = "Most histories give 1388 as the foundation year, two years after Heidelberg opened. The founding bull and the council's support are the two steps the usual accounts name.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.SYSTEM,
        section = "Parliaments",
        title = "The Merciless Parliament of 1388, which condemned Richard II's advisers",
        originalTitle = "The Merciless Parliament",
        author = "The lords appellant of England",
        year = 1388,
        summary = "The Merciless Parliament of 1388 condemned several of Richard II's close advisers, and some of them were executed.",
        details = "The lords appellant used the parliament to remove the king's favourites, and it sat in the spring of 1388. The name comes from the severity of its sentences.",
    ),

)
