package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1240 to 1250: Sicilian court poetry, Li Ye's celestial element and a Visigothic law code put into Castilian. */
val db80Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 80,
        kind = Kind.LANGUAGE,
        section = "Poetry",
        title = "The Sicilian court poets of Frederick II and the sonnet",
        originalTitle = "Scuola siciliana",
        author = "Giacomo da Lentini and the court poets of Palermo",
        year = 1240,
        summary = "Court poets at the Sicilian court of Frederick II wrote Italian love lyrics in the 1230s and 1240s, and Giacomo da Lentini is credited with the sonnet form.",
        details = "The court was at Palermo, and most of the poems survive in copies made in Tuscany that changed their spelling. The group is usually dated from about 1230 to about 1250, and accounts differ on whether Giacomo invented the sonnet or only shaped it.",
    ),

    entry(
        databaseId = 80,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "The celestial element, Li Ye's sign for an unknown quantity",
        originalTitle = "Tian yuan",
        author = "Li Ye",
        year = 1248,
        summary = "Li Ye's Sea Mirror of Circle Measurements, finished in 1248, uses the celestial element, a sign on the counting board for an unknown quantity, to set up equations in one unknown.",
        details = "Li Ye was a scholar of the Jin state who declined office under the Mongols, and his book gives worked problems that show how the sign was placed. Later writers such as Zhu Shijie extended the notation to four unknowns, and accounts differ on how far its rules can be rebuilt from the problems alone.",
        rare = true,
    ),

    entry(
        databaseId = 80,
        kind = Kind.LANGUAGE,
        section = "Law",
        title = "The Fuero Juzgo, the Visigothic law code put into Castilian",
        originalTitle = "Fuero Juzgo",
        author = "Ferdinand III of Castile and the council of Cordoba",
        year = 1241,
        summary = "Ferdinand III had the Visigothic law code turned into Castilian, and the Fuero Juzgo was granted to the city of Cordoba in 1241.",
        details = "The traditional date is 1241, but the day is disputed: one charter is dated 3 March 1241 and another source gives 4 April 1241. Fragments of an earlier Catalan rendering show that vernacular versions of the code existed before this one.",
        rare = true,
    ),

)
