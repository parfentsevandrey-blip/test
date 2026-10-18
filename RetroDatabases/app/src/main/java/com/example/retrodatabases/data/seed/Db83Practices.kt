package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1210 to 1220: the interdict on England, yearly confession under the Lateran decree and the friars' chapter of mats. */
val db83Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 83,
        kind = Kind.PRACTICE,
        section = "Church",
        title = "The interdict that closed the churches of England, 1208 to 1214",
        originalTitle = "Interdictum Angliae",
        author = "Pope Innocent III and King John",
        year = 1214,
        summary = "The papal interdict on England, laid in 1208, closed most churches and suspended most sacraments until it was lifted in 1214.",
        details = "The quarrel began over the choice of Stephen Langton as archbishop of Canterbury, and John's submission in 1213 was followed by the lifting of the interdict in 1214. During the interdict baptism and last rites remained open, while most public services stopped.",
    ),

    entry(
        databaseId = 83,
        kind = Kind.PRACTICE,
        section = "Church",
        title = "Yearly confession ordered by the Fourth Lateran Council",
        originalTitle = "Omnis utriusque sexus",
        author = "The Fourth Lateran Council",
        year = 1215,
        summary = "The Fourth Lateran Council required every Christian who had reached the age of discretion to confess at least once a year to a priest.",
        details = "The same rule asked the faithful to receive communion at least at Easter, and it became the basis of the annual confession practised through the later Middle Ages. The decree is known by its opening words, Omnis utriusque sexus.",
    ),

    entry(
        databaseId = 83,
        kind = Kind.PRACTICE,
        section = "Religious life",
        title = "The Franciscan chapter of mats at the Portiuncula, 1217",
        originalTitle = "Capitolo delle stuoie",
        author = "Francis of Assisi and the friars",
        year = 1217,
        summary = "In 1217 the Franciscan friars held a great gathering at the Portiuncula near Assisi, and many of them lived in huts made of branches and mats.",
        details = "The assembly is called the chapter of mats after those shelters, and later chroniclers describe it in lively detail. Accounts differ widely on how many friars came, so no number is given here.",
        rare = true,
    ),

)
