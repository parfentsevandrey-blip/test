package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1041 to 1046: two Latin works of Wipo for Henry III and an Arabic Sufi manual by Qushayri. */
val db100Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 100,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Wipo's Gesta Chuonradi, a biography of Emperor Conrad II written for Henry III",
        originalTitle = "Gesta Chuonradi II imperatoris",
        author = "Wipo of Burgundy",
        year = 1046,
        summary = "Wipo's Gesta Chuonradi is a Latin biography of the emperor Conrad II, who died in 1039, and it was handed to Conrad's son Henry III in 1046.",
        details = "Accounts date the handover to Henry III to 1046, shortly after Henry's coronation, while the year the writing began is less certain. Wipo had served Conrad as a chaplain, and the work praises the emperor's rule.",
        rare = true,
    ),

    entry(
        databaseId = 100,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Wipo's Tetralogus, a Latin poem of praise and advice for the young Henry III",
        originalTitle = "Tetralogus",
        author = "Wipo of Burgundy",
        year = 1041,
        summary = "Wipo's Tetralogus is a Latin poem in hexameters that praises the young king Henry III and offers him advice on how to rule.",
        details = "The poem is dated to 1041, the year it is said to have reached Henry, five years before his imperial coronation of 1046. It is advice literature in verse, written for a king who was not yet emperor.",
        rare = true,
    ),

    entry(
        databaseId = 100,
        kind = Kind.LANGUAGE,
        section = "Sufism",
        title = "Qushayri's Risala, an Arabic manual of Sufi terms and practice",
        originalTitle = "Risala al-Qushayriyya",
        author = "Abu al-Qasim al-Qushayri",
        year = 1046,
        summary = "Qushayri's Risala is an Arabic manual that defines the terms, states and practices of the Sufi path for its readers.",
        details = "Qushayri, a scholar of Nishapur, finished the work in 438 AH, which falls in 1046. Its chapters define the technical vocabulary of Sufism, and the book became one of the standard Sufi handbooks.",
        rare = true,
    ),

)
