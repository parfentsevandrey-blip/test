package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1470 to 1480: the printing presses and type of Venice, Buda, Naples, Augsburg, Venice, Oxford and Lyon, and a Mexica calendar stone. */
val db57Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 57,
        kind = Kind.DEVICE,
        section = "Type",
        title = "Jenson's roman type, cut at Venice in 1470",
        originalTitle = "Eusebius, Praeparatio evangelica",
        author = "Nicolas Jenson",
        year = 1470,
        summary = "Nicolas Jenson's roman letter, first used at Venice in 1470, became the model for roman type in later printing.",
        details = "Jenson, a Frenchman working at Venice, printed Eusebius's Praeparatio evangelica in 1470 in roman letters. Later punch-cutters copied and adapted the design, and accounts differ on how much of the letter was new.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.DEVICE,
        section = "Presses",
        title = "Andreas Hess's press at Buda, the first in Hungary",
        originalTitle = "Chronica Hungarorum",
        author = "Andreas Hess",
        year = 1473,
        summary = "The press that Andreas Hess set up at Buda printed the Chronica Hungarorum in June 1473, the first book printed in Hungary.",
        details = "Printing was finished on 5 June 1473, the eve of Pentecost, with the backing of Archbishop Janos Vitez and Provost Laszlo Karai. One estimate says about ten copies survive, and accounts give the page count of the book in different ways.",
        rare = true,
    ),

    entry(
        databaseId = 57,
        kind = Kind.DEVICE,
        section = "Presses",
        title = "Riessinger's press at Naples from 1471",
        originalTitle = "Sixtus Riessinger",
        author = "Sixtus Riessinger",
        year = 1471,
        summary = "Sixtus Riessinger, a German printer who had worked at Rome, set up a press at Naples, where his first dated books appeared in 1471.",
        details = "His Naples output ran to more than fifty imprints by 1478, by one count. Accounts differ on when he left Rome and on which Naples book came first, so the record uses the year of his first dated imprints.",
        rare = true,
    ),

    entry(
        databaseId = 57,
        kind = Kind.DEVICE,
        section = "Presses",
        title = "Johann Schussler's press at Augsburg in 1471",
        originalTitle = "Johann Schussler",
        author = "Johann Schussler",
        year = 1471,
        summary = "Johann Schussler's press at Augsburg printed a Latin farming treatise in 1471.",
        details = "Accounts give the printing as February or March 1471, and some accounts call the Augsburg book the first printed farming treatise. The record gives no other details of the press, so its wider output is not described here.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.DEVICE,
        section = "Presses",
        title = "Erhard Ratdolt's press at Venice from 1476",
        originalTitle = "Erhardus Ratdolt",
        author = "Erhard Ratdolt",
        year = 1476,
        summary = "Erhard Ratdolt, a German printer, set up a press at Venice in 1476 and became known for decorated title pages.",
        details = "His Venetian books include calendars and works of mathematics, and his title pages with borders and diagrams were admired by later printers. The record names the press and the year only, and it does not list his output.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.DEVICE,
        section = "Presses",
        title = "The Oxford press of Theodoric Rood, 1478",
        originalTitle = "Expositio Sancti Hieronymi in Symbolum Apostolorum",
        author = "Theodoric Rood",
        year = 1478,
        summary = "The first press at Oxford, run by Theodoric Rood, printed Jerome's exposition of the Creed, a book usually dated 1478.",
        details = "Some older catalogues give the same book the date 1468, and the difference of ten years is still discussed in bibliographies. Rood worked with partners at the press, and the record names only the printer.",
        rare = true,
    ),

    entry(
        databaseId = 57,
        kind = Kind.DEVICE,
        section = "Calendars",
        title = "The Mexica calendar stone, carved about 1479",
        originalTitle = "Piedra del Sol",
        author = "Mexica carvers",
        year = 1479,
        summary = "The Mexica calendar stone carries the day signs of the 260-day count in carved rings, and one account dates its carving to 1479.",
        details = "Other accounts place the carving in a range from 1502 to 1520, so the date is disputed. The stone was found in Mexico City in 1790 and is now kept in the capital's national museum.",
        rare = true,
    ),

    entry(
        databaseId = 57,
        kind = Kind.DEVICE,
        section = "Presses",
        title = "The Lyon press of Guillaume Le Roy, 1473",
        originalTitle = "Guillaume Le Roy",
        author = "Guillaume Le Roy",
        year = 1473,
        summary = "Guillaume Le Roy set up a press at Lyon in 1473, one of the early printing houses of France outside Paris.",
        details = "Lyon was a centre of trade fairs in the 1400s. The record names the press and the year only, and it gives no details of the first book printed there.",
        rare = true,
    ),

)
