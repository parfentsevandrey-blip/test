package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Writings of 990 to 1000: a Latin history of Reims, Aelfric's First Series of English homilies and the Beowulf manuscript. */
val db105Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 105,
        kind = Kind.LANGUAGE,
        section = "Latin history",
        title = "Richer of Reims's Historiae, four books of Frankish history from 888 to 998",
        originalTitle = "Historiarum libri quatuor",
        author = "Richer of Reims",
        year = 996,
        summary = "Richer of Reims wrote four books of Latin history covering the years 888 to 998, and the account names the teaching instruments that Gerbert of Aurillac used at Reims.",
        details = "The history is usually dated to about 996, and its Latin title is Historiarum libri quatuor. Its list of Gerbert of Aurillac's teaching tools at Reims names a celestial globe, an observation hemisphere and two armillary spheres.",
        rare = true,
    ),

    entry(
        databaseId = 105,
        kind = Kind.LANGUAGE,
        section = "Anglo-Saxon England",
        title = "Aelfric's Catholic Homilies, the First Series, English sermons for the church year",
        originalTitle = "Catholic Homilies, First Series",
        author = "Aelfric of Eynsham",
        year = 990,
        summary = "Aelfric's First Series of Catholic Homilies is a set of sermons in English prose for the church year, written in Anglo-Saxon England about 990.",
        details = "The series is usually dated to the years around 990, and a Second Series followed a few years later. Writing the sermons in English made church teaching available to readers who did not read Latin, which is why the set matters for the history of English prose.",
        rare = true,
    ),

    entry(
        databaseId = 105,
        kind = Kind.LANGUAGE,
        section = "Anglo-Saxon England",
        title = "The Beowulf manuscript, the Nowell Codex, copied in England about 1000",
        originalTitle = "Beowulf",
        author = "Unknown scribes",
        year = 1000,
        summary = "The only manuscript of the Old English poem Beowulf is a codex of Old English texts copied about 1000, and it survives with fire damage from 1731.",
        details = "Palaeographers date the hands of the codex to around 1000, but the poem itself is older and its date of composition is not fixed, so the year given is the date of the manuscript. The codex, catalogued as Cotton Vitellius A.xv, was damaged in a fire of 1731 that scorched its edges.",
    ),

)
