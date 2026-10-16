package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1380 to 1390: Latin commentary and verse, a Middle English poem and its C text, and an Icelandic book of kings' sagas. */
val db66Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 66,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Benvenuto da Imola's Latin commentary on Dante, finished in 1380",
        originalTitle = "Commentum super Dantis Aldigherii Comoediam",
        author = "Benvenuto da Imola",
        year = 1380,
        summary = "Benvenuto da Imola completed his Latin commentary on Dante's Commedia in 1380, a long gloss on the poem written for readers of Latin.",
        details = "Benvenuto explained the poem line by line in Latin, and 1380 is the date usually given for the completed text. The commentary belongs to the line of Dante glosses that grew up in the decades after the poet's death.",
        rare = true,
    ),

    entry(
        databaseId = 66,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Wyclif's Trialogus, a Latin dialogue of three speakers",
        originalTitle = "Trialogus",
        author = "John Wyclif",
        year = 1382,
        summary = "John Wyclif's Trialogus, a Latin dialogue among three speakers on theology and the church, is usually dated to 1382 and was revised in the years that followed.",
        details = "The work was written for learned readers in the schools, and its three speakers give the book its name. The date 1382 is the usual date of composition.",
        rare = true,
    ),

    entry(
        databaseId = 66,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Gower's Vox clamantis, a Latin poem on the rising of 1381",
        originalTitle = "Vox clamantis",
        author = "John Gower",
        year = 1382,
        summary = "John Gower's Vox clamantis is a Latin poem in verse that answers the rising of 1381, and it is usually dated to about 1382.",
        details = "Gower wrote the poem in Latin verse and treats the revolt as a judgment on sin in English society. The date about 1382 is the usual date of composition.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.LANGUAGE,
        section = "Middle English",
        title = "Langland's Piers Plowman, the C text of about 1385",
        originalTitle = "Piers Plowman, C text",
        author = "William Langland",
        year = 1385,
        summary = "The C text of Piers Plowman is William Langland's Middle English alliterative poem in its third major form, usually dated to about 1385 to 1387.",
        details = "Langland reworked the poem from the earlier A and B texts, and the C text is usually dated to about 1385 to 1387. The poem is written in the alliterative long line of the English tradition.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.LANGUAGE,
        section = "Middle English",
        title = "Chaucer's Troilus and Criseyde, a long English poem in rhyme royal",
        originalTitle = "Troilus and Criseyde",
        author = "Geoffrey Chaucer",
        year = 1385,
        summary = "Geoffrey Chaucer's Troilus and Criseyde is a long Middle English poem on a Trojan love story, usually dated to about 1385.",
        details = "Chaucer wrote the poem in the rhyme royal stanza of seven lines. The date about 1385 is the usual date of composition, and the poem tells its story in five books.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.LANGUAGE,
        section = "Old Norse",
        title = "The Flateyjarbok, an Icelandic book of kings' sagas begun in 1387",
        originalTitle = "Flateyjarbok",
        author = "Jon Hakonarson and Magnus Thorhallsson",
        year = 1387,
        summary = "The Flateyjarbok is a large Icelandic vellum book of sagas about the kings of Norway, begun in 1387 in Iceland.",
        details = "The book takes its name from the island of Flatey, where it was kept for a long time, and it was finished about 1395. The year 1387 is the usual date for its start.",
        rare = true,
    ),

)
