package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1210 to 1220: a Japanese essay of 1212, a French chronicle of the Fourth Crusade, an English chronicle, an Icelandic handbook of poetry and a Hebrew book of tales. */
val db83Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 83,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "Kamo no Chomei's Hojoki, an essay written from a small hut",
        originalTitle = "Hojoki",
        author = "Kamo no Chomei",
        year = 1212,
        summary = "Kamo no Chomei wrote the Hojoki in 1212, a short essay in Japanese on the passing of things and on his life in a small hut outside Kyoto.",
        details = "The title means a hut ten feet square, and Chomei describes the hut he built after leaving court service to live as a Buddhist recluse. The essay opens with a river that never stays the same, and the usual date for it is 1212.",
    ),

    entry(
        databaseId = 83,
        kind = Kind.LANGUAGE,
        section = "Old French",
        title = "Villehardouin's account of the Fourth Crusade, an early long history in French prose",
        originalTitle = "La Conquete de Constantinople",
        author = "Geoffroi de Villehardouin",
        year = 1210,
        summary = "Villehardouin wrote his account of the Fourth Crusade in French about 1210, one of the earliest long histories written in French prose.",
        details = "The chronicle tells how the crusaders turned against Constantinople and took it in 1204, and its author was a marshal of Champagne who took part in the events. The date of about 1210 is an estimate, since the work was finished in the years before the author's death, and accounts differ on the year of writing.",
        rare = true,
    ),

    entry(
        databaseId = 83,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "Judah al-Harizi's Tahkemoni, Hebrew tales in rhymed prose",
        originalTitle = "Tahkemoni",
        author = "Judah al-Harizi",
        year = 1218,
        summary = "Judah al-Harizi's Tahkemoni is a collection of Hebrew tales in rhymed prose, finished about 1218, that imitates the Arabic assemblies of al-Hariri.",
        details = "The episodes mix rhymed prose and verse, and the book shows how Hebrew could carry the forms of Arabic literature. The date of about 1218 is an estimate for the completion of the work.",
        rare = true,
    ),

    entry(
        databaseId = 83,
        kind = Kind.LANGUAGE,
        section = "Middle English",
        title = "Layamon's Brut, an English chronicle of Britain in long lines",
        originalTitle = "Brut",
        author = "Layamon",
        year = 1215,
        summary = "Layamon's Brut is a history of Britain in English, written in long lines about 1215, and it is among the earliest long poems in English after the Norman Conquest.",
        details = "The poem adapts the French Roman de Brut of Wace and expands the material on King Arthur. Accounts differ on the years of composition, which are placed between about 1190 and about 1225, so 1215 is a rounded date.",
        rare = true,
    ),

    entry(
        databaseId = 83,
        kind = Kind.LANGUAGE,
        section = "Old Norse",
        title = "Snorri Sturluson's Prose Edda, a handbook of old myths and poetic words",
        originalTitle = "Snorra Edda",
        author = "Snorri Sturluson",
        year = 1220,
        summary = "Snorri Sturluson's Prose Edda, written in Icelandic about 1220, retells the old myths and teaches the poetic words that skaldic verse needs.",
        details = "The book has four parts: a prologue, Gylfaginning, Skaldskaparmal and Hattatal. Gylfaginning tells the myths, Skaldskaparmal teaches the kennings and names used in poetry, and Hattatal lists verse forms, and the date of about 1220 is an estimate, since accounts place the work between about 1220 and about 1225.",
    ),

)
