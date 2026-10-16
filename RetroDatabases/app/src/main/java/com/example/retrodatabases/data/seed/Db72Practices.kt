package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1320 to 1330: isorhythm in motets, a copper token coinage in Delhi, quodlibetal debates at Oxford, a royal pilgrimage in gold and a timber lantern at Ely. */
val db72Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 72,
        kind = Kind.PRACTICE,
        section = "Music",
        title = "Isorhythm, the repeated talea and color of the 1320s motet",
        originalTitle = "Isorhythm",
        author = "Philippe de Vitry and his circle",
        year = 1322,
        summary = "Isorhythm repeats a rhythmic pattern and a melody through the tenor of a motet, a composing practice that appears in the motets of the 1320s and that the new mensural music made possible.",
        details = "Motets of Philippe de Vitry and his circle are usually cited as early examples. The term itself is a modern word for the practice, and accounts differ on how early the method was used, so 1322 is a round date for its first clear examples.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.PRACTICE,
        section = "Money",
        title = "Muhammad bin Tughluq's copper tokens, the Delhi experiment of 1329",
        originalTitle = "Token currency",
        author = "Muhammad bin Tughluq",
        year = 1329,
        summary = "The sultan of Delhi, Muhammad bin Tughluq, put copper tokens in place of silver coin in 1329 or 1330, a monetary experiment that failed and was later withdrawn.",
        details = "Accounts differ between 1329 and 1330 for the start of the copper issue, so 1329 is the earliest date given. The Moroccan traveller Ibn Battuta describes the failure of the tokens in his account of India.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.PRACTICE,
        section = "Disputations",
        title = "Quodlibetal disputations at Oxford in the 1320s, debates on any question",
        originalTitle = "Quodlibeta",
        author = "William of Ockham and the Oxford masters",
        year = 1325,
        summary = "Oxford masters held quodlibetal disputations in the 1320s, public debates in which the audience could put any question, and the Quodlibeta of William of Ockham are among the best-known records.",
        details = "The form required a master to answer questions put by the audience on the day of the debate. The exact dates of the surviving sets are disputed, so 1325 is a middle date for the decade.",
        rare = true,
    ),

    entry(
        databaseId = 72,
        kind = Kind.PRACTICE,
        section = "Pilgrimage",
        title = "Mansa Musa's gold on the pilgrimage of 1324, a royal display",
        originalTitle = "Hajj of Mansa Musa",
        author = "Mansa Musa of Mali",
        year = 1324,
        summary = "Mansa Musa, ruler of Mali, went on pilgrimage to Mecca in 1324 with a great train and gave gold away on the way, and the journey is recorded by several Arabic writers.",
        details = "Accounts differ on the amount of gold and on the places where it was spent, so the value of the gifts is not agreed. The year 1324 is the date of the departure usually given.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.PRACTICE,
        section = "Building",
        title = "The Ely octagon, a timber lantern built after the crossing tower fell in 1322",
        originalTitle = "Ely octagon",
        author = "The masons and carpenters of Ely",
        year = 1322,
        summary = "After the central tower of Ely Cathedral fell in 1322, the crossing was rebuilt as an octagon with a timber lantern, a carpentry practice that let light into the church from above.",
        details = "The rebuilding is dated from 1322, and the octagon was finished in the decades after that. Timber framing was used over the crossing in place of a stone tower, and the work is a clear example of carpenters solving a structural problem.",
    ),

)
