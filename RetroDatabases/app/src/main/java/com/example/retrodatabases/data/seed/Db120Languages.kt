package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 840 to 850: the Oaths of Strasbourg of 842, the Nihon Koki of 840 and Ennin's diary of 847. */
val db120Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 120,
        kind = Kind.LANGUAGE,
        section = "Romance",
        title = "The Oaths of Strasbourg, the earliest surviving text in a Romance language",
        originalTitle = "Serments de Strasbourg",
        author = "Louis the German and Charles the Bald, recorded by Nithard",
        year = 842,
        summary = "The Oaths of Strasbourg, sworn in 842 by two Carolingian kings, are the earliest surviving text in a Romance language.",
        details = "On 14 February 842 Louis the German swore in the Romance tongue, so that the army of his brother Charles could follow it, and Charles swore in German for Louis's men. The two oaths survive in Nithard's Histories, a Latin history of the years around 840.",
    ),

    entry(
        databaseId = 120,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "Ennin's diary of his pilgrimage through Tang China, 838 to 847",
        originalTitle = "Nitto guho junrei koki",
        author = "Ennin, a Japanese monk",
        year = 847,
        summary = "Ennin's diary of his pilgrimage through Tang China, written in Chinese, covers the years 838 to 847 and records the suppression of Buddhism in 845.",
        details = "The diary follows a Japanese monk who searched for Buddhist texts and teachers across the Tang empire, and it ends with his return to Japan in 847. Its entries for 845 and 846 describe the suppression of monasteries and monks under Emperor Wuzong, which makes it an eyewitness record of the Huichang persecution. The year used here is 847, the year the journey ended and the record closes.",
        rare = true,
    ),

    entry(
        databaseId = 120,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Nihon Koki, Japan's third national history, written in Chinese",
        originalTitle = "Nihon Koki",
        author = "Court historians of the Heian period",
        year = 840,
        summary = "The Nihon Koki, the third of Japan's six national histories, was completed in 840 in classical Chinese and is arranged year by year.",
        details = "It covers the years 792 to 833 in forty volumes, of which only part survives. Like the other national histories it is annalistic, so each year has its own entries, and 840 is the completion year usually given.",
        rare = true,
    ),

)
