package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1420 to 1430: lifting machines and perspective apparatus of Florence, early clocks in Moravia and Sweden, a Hussite wagon fort, a rocket-driven bird in a war book and an equatorium. */
val db62Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 62,
        kind = Kind.DEVICE,
        section = "Lifting machines",
        title = "Brunelleschi's hoist for the dome of Florence",
        originalTitle = "Hoist for the dome of Santa Maria del Fiore",
        author = "Filippo Brunelleschi (attributed)",
        year = 1420,
        summary = "A hoist driven by oxen lifted the stone and brick for the dome of Florence Cathedral, whose building began in 1420.",
        details = "The dome was begun in 1420, and the lifting machinery is attributed to Brunelleschi and known mainly from later drawings and accounts. Its exact design is disputed among historians, so the record does not describe its gears.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.DEVICE,
        section = "Perspective",
        title = "Brunelleschi's panel and mirror for perspective",
        originalTitle = "Panel of the Florence Baptistery",
        author = "Filippo Brunelleschi",
        year = 1420,
        summary = "Brunelleschi's painted panel of the Florence Baptistery, viewed through a peephole and a mirror, was an early demonstration of linear perspective.",
        details = "The demonstration is usually dated to about 1415 to 1420, and accounts differ, so the year 1420 is used here. The panel is lost, and its method is known from a later biography of Brunelleschi written by Antonio Manetti.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The Olomouc astronomical clock",
        originalTitle = "Olomoucky orloj",
        author = "Unknown makers",
        year = 1420,
        summary = "The Olomouc astronomical clock in Moravia is a late medieval dial whose first build is dated to 1420 or 1422 by different accounts.",
        details = "Some accounts give 1420 and others 1422, and the face was rebuilt many times, so the first date is disputed.",
        rare = true,
    ),

    entry(
        databaseId = 62,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The astronomical clock of Lund Cathedral",
        originalTitle = "Lunds astronomiska urverk",
        author = "Unknown maker, possibly Nikolaus Lilienfeld",
        year = 1424,
        summary = "Lund Cathedral's astronomical clock in Sweden was probably made in the 1420s, and its first written mention dates from 1442.",
        details = "The making is estimated at about 1423 to 1425, while a heritage guide gives an origin near 1420, so the year 1424 is used here. The maker's name appears only as a possibility in some accounts.",
        rare = true,
    ),

    entry(
        databaseId = 62,
        kind = Kind.DEVICE,
        section = "Field fortifications",
        title = "The Hussite wagon fort",
        originalTitle = "Wagenburg",
        author = "Hussite field commanders",
        year = 1420,
        summary = "The Hussite wagon fort was a ring of carts, chained together and manned with men and guns, that protected the infantry of the Hussite wars that began in 1420.",
        details = "Hussite armies drew their carts into a ring for defence from about 1420, and the wars ran until 1434. Accounts of the first use of the wagon fort differ, and the year 1420 is used here for the start of the wars.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.DEVICE,
        section = "War machines",
        title = "Fontana's rocket-driven bird",
        originalTitle = "Bellicorum instrumentorum liber",
        author = "Giovanni Fontana",
        year = 1420,
        summary = "Fontana's war book of about 1420 pictures machines of fire, among them a bird driven by a rocket.",
        details = "The manuscript is kept in Munich as Cod.icon. 242 and is dated to about 1420. Its drawings include a rocket-powered bird, and they show ideas on paper rather than machines that were built.",
        rare = true,
    ),

    entry(
        databaseId = 62,
        kind = Kind.DEVICE,
        section = "Astronomical calculators",
        title = "The equatorium of Johannes de Lineriis",
        originalTitle = "Equatorium",
        author = "Johannes de Lineriis",
        year = 1422,
        summary = "An equatorium is a brass and wood calculator that finds planetary positions by turning arms over a disc, and Johannes de Lineriis wrote on one in the early 1420s.",
        details = "The treatise is usually dated to the early 1420s, and the instrument let a user find a planet's place without a table for each date. Accounts of Johannes's life are thin, so both the date and his career are uncertain.",
        rare = true,
    ),

    entry(
        databaseId = 62,
        kind = Kind.DEVICE,
        section = "Marble transport",
        title = "Brunelleschi's barge for marble",
        originalTitle = "Barge for the cathedral marble",
        author = "Filippo Brunelleschi",
        year = 1421,
        summary = "Brunelleschi is credited with a large barge to carry marble for the cathedral works on the river.",
        details = "The barge is known from a later biography of Brunelleschi, and accounts report its fate in different ways. The year 1421 is the date given for the project in the usual accounts.",
        rare = true,
    ),

)
