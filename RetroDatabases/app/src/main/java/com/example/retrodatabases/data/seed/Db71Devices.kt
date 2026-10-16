package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1333 to 1338: a Milan clock, a Damascus quadrant, Lorenzetti's hourglass and a book of war machines. */
val db71Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 71,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The clock of San Gottardo in Milan, described in 1335",
        originalTitle = "Orologio di San Gottardo",
        author = "Galvano Fiamma, Dominican chronicler",
        year = 1335,
        summary = "Galvano Fiamma's account of 1335 describes a mechanical clock at the church of San Gottardo in Milan that strikes a bell for the hours.",
        details = "Accounts date the clock in different ways, to about 1330 and to a commission of 1336, so 1335 is the year of Fiamma's account rather than a settled date for the clock. Fiamma lived from about 1283 to 1344 and wrote Milanese chronicles.",
        rare = true,
    ),

    entry(
        databaseId = 71,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The brass astrolabic quadrant made for Damascus, dated 1333 to 1334",
        originalTitle = "Astrolabic quadrant",
        author = "Unknown maker, for the timekeeper of the Great Umayyad Mosque",
        year = 1333,
        summary = "A brass astrolabic quadrant calibrated to the latitude of Damascus was made for the timekeeper of the Great Umayyad Mosque, and the British Museum dates it to 1333 to 1334.",
        details = "The quadrant is calibrated to a latitude of 33 degrees 30 minutes, and the museum's production window of 1333 to 1334 equals the Islamic year 734. Instruments of this kind let the user read a height and turn it into a time or a direction.",
        rare = true,
    ),

    entry(
        databaseId = 71,
        kind = Kind.DEVICE,
        section = "Time",
        title = "The hourglass in Lorenzetti's fresco of Good Government in Siena",
        originalTitle = "Allegoria del buon governo",
        author = "Ambrogio Lorenzetti",
        year = 1338,
        summary = "Ambrogio Lorenzetti's fresco in Siena's Palazzo Pubblico, dated 1338, shows an hourglass in the hand of Temperance and is usually cited as the earliest firmly dated European image of one.",
        details = "Most sources date the fresco to 1338, while some give a span of 1337 to 1339. The hourglass is held by the personification of Temperance among the figures of the painted allegory.",
    ),

    entry(
        databaseId = 71,
        kind = Kind.DEVICE,
        section = "War",
        title = "Guido da Vigevano's Texaurus, an illustrated book of war machines for Philip VI",
        originalTitle = "Texaurus regis Francie",
        author = "Guido da Vigevano",
        year = 1335,
        summary = "Guido da Vigevano, an Italian physician, completed in 1335 an illustrated book of war machines and devices for the king of France, Philip VI.",
        details = "The title means the treasure of the king of France, and the book proposes siege engines and other devices, drawn in its illustrations. Copies of the illustrated book survive.",
        rare = true,
    ),

)
