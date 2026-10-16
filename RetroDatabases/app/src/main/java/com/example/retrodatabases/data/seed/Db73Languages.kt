package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1310 to 1317: an Old French satire with music, a Latin music treatise, a Persian mystical poem and a world history, Dante's Latin treatise and a Florentine chronicle in Italian. */
val db73Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 73,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "The Roman de Fauvel, a satire in Old French with music",
        originalTitle = "Le Roman de Fauvel",
        author = "Gervais du Bus",
        year = 1314,
        summary = "Gervais du Bus wrote the Roman de Fauvel in Old French as a satire on the royal court, in which a horse named Fauvel stands for a list of vices.",
        details = "The poem is usually dated to the years 1310 to 1314, and its surviving manuscripts add songs and motets to the text, which makes them important for the music of the period. The name of the horse stands for a list of vices, and that name carries the joke of the whole poem.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Marchetto of Padua's Lucidarium, a music treatise of 1317",
        originalTitle = "Lucidarium in arte musice plane",
        author = "Marchetto of Padua",
        year = 1317,
        summary = "Marchetto of Padua completed his Latin Lucidarium in 1317, a treatise on the theory of music that explains intervals and the reading of notes.",
        details = "His later Pomerium of 1318 follows the same line of teaching, and both treatises are used to study the notation of the early fourteenth century. The date 1317 is the one given for the Lucidarium.",
        rare = true,
    ),

    entry(
        databaseId = 73,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Shabistari's Gulshan-i raz, a Persian poem of mystical answers",
        originalTitle = "Gulshan-i raz",
        author = "Mahmud Shabistari",
        year = 1317,
        summary = "Mahmud Shabistari wrote the Gulshan-i raz in Persian verse in 1317, a poem of answers to questions about the mystical path that became a common introduction to Sufi thought.",
        details = "Its couplets treat God, the self and the stages of the mystic journey. The poem was written as a reply to questions, and 1317 is the year usually given for its composition.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Rashid al-Din's Jami al-tawarikh, a world history in Persian",
        originalTitle = "Jami al-tawarikh",
        author = "Rashid al-Din",
        year = 1310,
        summary = "Rashid al-Din, a physician and minister of the Ilkhans at Tabriz, compiled the Jami al-tawarikh in Persian, a history that begins with the Mongols and then turns to the wider world.",
        details = "The work was written in parts during the first decade of the fourteenth century, and accounts differ on the date of its completion. Rashid al-Din was put to death in 1318, and the year 1310 is given here for the world history.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Dante's Monarchia, a Latin treatise on world government",
        originalTitle = "Monarchia",
        author = "Dante Alighieri",
        year = 1313,
        summary = "Dante wrote the Monarchia in Latin in about 1312 to 1313, a treatise in three books arguing that world government belongs to the emperor rather than to the pope.",
        details = "The book is a work of political argument in Latin, and it answers the claims of the papal party with appeals to reason and to scripture. The years 1312 to 1313 are the ones usually assigned to its composition.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.LANGUAGE,
        section = "Italian",
        title = "Dino Compagni's Cronica, a chronicle of Florence in Italian",
        originalTitle = "Cronica delle cose occorrenti ne' tempi suoi",
        author = "Dino Compagni",
        year = 1312,
        summary = "Dino Compagni wrote his Cronica in Italian in the years around 1310 to 1312, an account of the faction fights of Florence by a citizen who had lived through them.",
        details = "The chronicle covers the years from about 1280 to about 1312 and is valued for its eyewitness detail. Accounts differ on the date of composition, so the year 1312 is the latest date given here.",
    ),

)
