package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1514 to 1518: Werner's heart-shaped projection, Stoeffler's almanac, two maps of 1515 and 1516, Erasmus's Adages and Ries's first reckoning book. */
val db53Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 53,
        kind = Kind.TOOL,
        section = "Projections",
        title = "Werner's heart-shaped projection of the sphere, 1514",
        originalTitle = "Nova translatio primi libri geographiae Ptolemaei",
        author = "Johannes Werner",
        year = 1514,
        summary = "Werner's Nova translatio of 1514 gave a Latin version of the first book of Ptolemy's Geography and a method for drawing the sphere on a heart-shaped outline.",
        details = "The heart-shaped outline, later called the cordiform projection, keeps the parallels of the sphere as curved lines on a flat sheet. The 1515 world map that Johannes Stabius and Durer produced used a projection of this family.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.TOOL,
        section = "Almanacs",
        title = "Stoeffler's Calendarium Romanum magnum, the great Roman almanac",
        originalTitle = "Calendarium Romanum magnum",
        author = "Johannes Stoeffler",
        year = 1518,
        summary = "Stoeffler's almanac of 1518 gave calendar tables and prognostications, and it is known for forecasting a great flood in 1524.",
        details = "Its flood forecast for 1524 was widely talked about, and it became one of the best-known predictions of its age. Accounts differ on how seriously readers believed the forecast.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.TOOL,
        section = "Maps",
        title = "Waldseemuller's Carta marina, the sea chart of 1516",
        originalTitle = "Carta marina",
        author = "Martin Waldseemuller",
        year = 1516,
        summary = "Waldseemuller's Carta marina of 1516 is a large printed sea chart of the Atlantic and the coasts of the new lands, made for navigators.",
        details = "It was printed at Strasbourg in 1516 as a woodcut map. Accounts differ on how much of the new world it shows, and the chart was made for the sea rather than for the land.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.TOOL,
        section = "Distilling",
        title = "Brunschwig's book on distilling, the German edition of 1512",
        originalTitle = "Das Buch der waren kunst zu distillieren",
        author = "Hieronymus Brunschwig",
        year = 1512,
        summary = "Brunschwig's German book on distilling, printed in 1512, set out how to distil waters and spirits step by step, with woodcuts of the apparatus.",
        details = "Brunschwig had published the method in Latin in 1500, and the German edition brought it to physicians and apothecaries who did not read Latin. Accounts differ on how many of the woodcut apparatus designs were the author's own.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.TOOL,
        section = "Proverbs",
        title = "Erasmus's Adages, the enlarged Basel edition of 1515",
        originalTitle = "Adagiorum Chiliades",
        author = "Desiderius Erasmus",
        year = 1515,
        summary = "Erasmus's Adages, enlarged in its Basel edition of 1515, is a reference book of Greek and Latin proverbs with notes on each saying.",
        details = "The collection grew from a small Venice printing of 1508 into a much larger book in the Basel edition, so the number of sayings differs between the two. Accounts differ on the exact number of adages in the 1515 edition.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Adam Ries's first reckoning book, dated 1518",
        originalTitle = "Rechnung auff der linihen",
        author = "Adam Ries",
        year = 1518,
        summary = "Adam Ries's first reckoning book, dated 1518, taught arithmetic on ruled lines in German and began a long run of German reckoning books.",
        details = "Ries was based in Erfurt from 1518 as a reckoning master, and his later book on lines and pen passed 108 editions by 1656. The first edition of the 1518 book is not traced, so its date rests on the book's own dating.",
    ),

)
