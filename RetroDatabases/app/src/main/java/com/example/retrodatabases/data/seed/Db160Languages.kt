package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 440 to 450: Leo the Great's Tome to Flavian, Salvian's treatise on God's government and a Chinese book of anecdotes. */
val db160Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 160,
        kind = Kind.LANGUAGE,
        section = "Latin letter",
        title = "Pope Leo I's Tome to Flavian, the letter that set out the two natures of Christ",
        originalTitle = "Tomus ad Flavianum",
        author = "Pope Leo I",
        year = 449,
        summary = "Pope Leo I sent his Tome to Flavian of Constantinople in 449, a letter that set out Christ as one person with two natures, divine and human.",
        details = "Leo wrote the letter in 449, before the Second Council of Ephesus later that year, and the council under Dioscorus refused the request of Rome's envoys to have it read aloud. The Tome became the basis of the teaching that the Council of Chalcedon accepted in 451.",
    ),

    entry(
        databaseId = 160,
        kind = Kind.LANGUAGE,
        section = "Latin prose",
        title = "Salvian's On the Government of God, an eight-book Latin treatise that blamed Rome's sins for its losses",
        originalTitle = "De gubernatione Dei",
        author = "Salvian of Marseille",
        year = 440,
        summary = "Salvian of Marseille's De gubernatione Dei argues in eight books that God let the western provinces fall because of Roman vice.",
        details = "The work is usually dated to about 440, although estimates run from about 439 to about 451. Salvian wrote it in Marseille and used the Vandal and Gothic conquests as evidence that God was judging the Romans, and its later books attack the tax officials and the rich.",
        rare = true,
    ),

    entry(
        databaseId = 160,
        kind = Kind.LANGUAGE,
        section = "Chinese prose",
        title = "Liu Yiqing's Shishuo xinyu, short anecdotes of gentlemen of the Wei and Jin courts",
        originalTitle = "Shishuo xinyu",
        author = "Liu Yiqing",
        year = 440,
        summary = "Liu Yiqing's Shishuo xinyu gathers short anecdotes about the talk and conduct of gentlemen of the Wei and Jin periods, arranged under headings of character.",
        details = "Liu Yiqing (403 to 444) was a prince of the Liu Song court, and the work is usually dated to the 430s or early 440s, so 440 is used here as a round date within that range. The surviving text carries a later commentary by Liu Jun, and its headings name qualities such as virtue, speech and literature.",
        rare = true,
    ),

)
