package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1530 to 1540: Apian's paper wheels of 1533, three instruments of Fine's 1532 book, Munster's sundial book, a proposed longitude clock, Biringuccio's furnaces and a press in Mexico. */
val db51Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 51,
        kind = Kind.DEVICE,
        section = "Instruments",
        title = "Apian's Instrument Buch of paper wheels",
        originalTitle = "Instrument Buch",
        author = "Peter Apian",
        year = 1533,
        summary = "Apian's Instrument Buch of 1533 was a book of paper instruments, with wheels that turn on a pin to answer questions about the sky and the calendar.",
        details = "The book was printed at Ingolstadt, and the first edition is dated 1533 in the usual accounts, although catalogues credit the imprint to Peter Apian or to his press. Its paper wheels, called volvelles, were turned by the reader to find a time or a position in the sky.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.DEVICE,
        section = "Instruments",
        title = "Fine's new quadrant in the Protomathesis of 1532",
        originalTitle = "Protomathesis",
        author = "Oronce Fine",
        year = 1532,
        summary = "Oronce Fine's Protomathesis of 1532 treats a new quadrant, an instrument for measuring the altitude of the sun and the stars.",
        details = "The new quadrant is the subject of the book's last section, whose title page is dated 1531, while the first collected edition is dated 1532. The record describes the instrument as the book presents it, not a surviving object.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.DEVICE,
        section = "Surveying",
        title = "Fine's cross-staff in the Protomathesis of 1532",
        originalTitle = "Protomathesis",
        author = "Oronce Fine",
        year = 1532,
        summary = "Oronce Fine's Protomathesis of 1532 illustrates the cross-staff, a rod with a sliding crosspiece for measuring the height of the sun or a star.",
        details = "The Protomathesis was printed as a collected edition in 1532, and its illustrations show instruments of the period, among them the cross-staff. The cross-staff was an older device, so the book shows a tool already in use rather than one invented by Fine.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.DEVICE,
        section = "Sundials",
        title = "Fine's sundial woodcuts in the Protomathesis of 1532",
        originalTitle = "Protomathesis",
        author = "Oronce Fine",
        year = 1532,
        summary = "The woodcuts of Oronce Fine's Protomathesis of 1532 include sundials, which tell the hour by the shadow of a gnomon on a flat or curved plane.",
        details = "The book was printed as a collected edition in 1532, and a title page dated 1531 is attached to its last part, the treatise on the new quadrant. Accounts differ on the year of the book, and its sundials appear as woodcut figures rather than as working instruments.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.DEVICE,
        section = "Sundials",
        title = "Munster's Horologiographia on the making of sundials",
        originalTitle = "Horologiographia",
        author = "Sebastian Munster",
        year = 1531,
        summary = "Munster's Horologiographia of 1531 taught readers how to lay out sundials on walls and planes, so that the shadow of a gnomon marks the hours.",
        details = "The book was printed at Basel in 1531, and it sets out the drawing of hour lines on dials for different surfaces. Clockmakers and surveyors of the period also needed that skill.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.DEVICE,
        section = "Longitude",
        title = "Gemma Frisius's clock for finding longitude",
        originalTitle = "De principiis astronomiae et cosmographiae",
        author = "Gemma Frisius",
        year = 1530,
        summary = "Gemma Frisius proposed in 1530 that a clock carried from a home town could give the longitude of a distant place by the difference in local time.",
        details = "The proposal is printed in his book of 1530 in the usual accounts. Other accounts date the explicit proposal of a portable timepiece to the 1553 Antwerp edition, and reliable clocks for ships came only with the work of John Harrison in the eighteenth century.",
        rare = true,
    ),

    entry(
        databaseId = 51,
        kind = Kind.DEVICE,
        section = "Metalworking",
        title = "Biringuccio's furnaces and bellows for smelting",
        originalTitle = "Pirotechnia",
        author = "Vannoccio Biringuccio",
        year = 1540,
        summary = "Biringuccio's Pirotechnia of 1540 describes the furnaces and bellows used to smelt and cast metals, and it is an early printed guide to metalworking machines.",
        details = "The book was printed at Venice in 1540, and it covers smelting, casting and the working of metals by fire. Its furnaces and bellows are working designs of the period rather than records of one particular workshop.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Juan Pablos's printing press in Mexico City",
        originalTitle = "Breve y mas compendiosa doctrina Christiana en lengua Mexicana y Castellana",
        author = "Juan Pablos",
        year = 1539,
        summary = "Juan Pablos set up the first printing press in Mexico City in 1539, the first press in the Americas, which printed a bilingual Christian doctrine in Nahuatl and Spanish.",
        details = "Pablos was an Italian printer who came from Seville with equipment sent by Juan Cromberger, and the first known imprint is a catechism credited to Juan de Zumarraga. Accounts differ on the date, since a plaque on the building credits Viceroy Antonio de Mendoza with the first press in 1536.",
        rare = true,
    ),

)
