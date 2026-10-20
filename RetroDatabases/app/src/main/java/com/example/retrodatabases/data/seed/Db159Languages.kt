package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Latin texts of 450 to 456: Patrick's Confessio (placed at 450) and Sidonius's panegyric on Avitus of 456. */
val db159Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 159,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Saint Patrick's Confessio, his own account of his mission to Ireland",
        originalTitle = "Confessio",
        author = "Saint Patrick",
        year = 450,
        summary = "Patrick's Confessio is a short Latin account of his life and mission in Ireland, written in the second half of the fifth century to answer his critics.",
        details = "The sources do not fix the year of the Confessio, so 450 is the year of the anchor, the start of the second half of the fifth century, and not a date of writing. The text survives in the Book of Armagh, a manuscript copied about 807, and it replies to critics who challenged the author's background and his work in Ireland. Scholars still argue about the chronology of Patrick's life, so the placed year marks a window rather than a day.",
    ),

    entry(
        databaseId = 159,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Sidonius Apollinaris's verse panegyric on the emperor Avitus, recited at Rome",
        originalTitle = "Panegyricus dictus Avito",
        author = "Sidonius Apollinaris",
        year = 456,
        summary = "Sidonius Apollinaris recited his Latin verse panegyric on the emperor Avitus at Rome on 1 January 456, the day Avitus began his consulship.",
        details = "Avitus had become emperor in 455 with Visigothic backing, and the long poem praises him in hexameter verse. It is one of Sidonius's three long panegyrics, and 1 January 456 is the usual date given for its recitation. Avitus was overthrown within two years, so the poem stands near the end of his short reign.",
        rare = true,
    ),

)
