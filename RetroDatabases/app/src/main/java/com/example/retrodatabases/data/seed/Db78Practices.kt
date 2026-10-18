package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1260 to 1270: the rules of the Paris crafts compiled in 1268 and the pigeon service of the Mamluk sultan Baybars in the early 1260s. */
val db78Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 78,
        kind = Kind.PRACTICE,
        section = "Crafts",
        title = "Etienne Boileau's Book of the Trades, the rules of the Paris crafts in 1268",
        originalTitle = "Le Livre des metiers",
        author = "Etienne Boileau",
        year = 1268,
        summary = "Etienne Boileau, provost of Paris, compiled the rules of the Paris crafts in 1268, setting out who could practise a trade and how masters trained apprentices.",
        details = "The book records the statutes of the crafts as the provost's court had recognised them, with rules on masters, apprentices and the sale of goods. Its date is the year of compilation, and it records earlier practice rather than a new code of law.",
    ),

    entry(
        databaseId = 78,
        kind = Kind.PRACTICE,
        section = "Messages",
        title = "Baybars's pigeon post and horse relays for official messages, from about 1260",
        originalTitle = "Barid",
        author = "Baybars and the Mamluk court",
        year = 1260,
        summary = "Early in the reign of Sultan Baybars the Mamluk state restored its horse relays and set up a pigeon service to carry official messages between Egypt and Syria.",
        details = "No single year is given for the pigeon service, so the date is approximate and covers the early reign, from 1260 to 1277. Use was restricted to the sultan and his provincial representatives, and accounts differ on delivery times, with one giving four days or less to Damascus and another a week on the Cairo to Damascus route.",
        rare = true,
    ),

)
