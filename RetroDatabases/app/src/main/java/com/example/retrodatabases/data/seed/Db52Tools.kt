package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1520 to 1530: Duerer's geometry and proportion books, Apian's Cosmographia and merchant's reckoning, Rudolff's German algebra and Werner's conics. */
val db52Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 52,
        kind = Kind.TOOL,
        section = "Geometry",
        title = "Duerer's Instruction in Measurement, the geometry book",
        originalTitle = "Underweysung der Messung mit dem Zirckel und Richtscheyt",
        author = "Albrecht Duerer",
        year = 1525,
        summary = "Duerer's book of measurement with compass and ruler taught painters and craftsmen the constructions of lines, solids and perspective, in German and in print.",
        details = "The book was printed at Nuremberg in 1525, and its fourth part shows the drawing aids of the workshop, including the frame with a grid. The constructions of polygons and curves in it were used for generations, though accounts differ on how far Duerer's methods were new.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Apian's Cosmographia, the book of the world",
        originalTitle = "Cosmographia",
        author = "Peter Apian",
        year = 1524,
        summary = "Apian's Cosmographia of 1524 was a textbook of the world and the heavens, with paper wheels for working out positions.",
        details = "The Cosmographia was printed at Landshut in 1524 by Johann Weyssenburger, and it set out the Earth, the heavens and the regions of the world for students. Its paper wheels were the most used part of the book, and later editions in other cities followed the first printing.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Rudolff's Coss, the German algebra of 1525",
        originalTitle = "Behend und Huebsch Rechnung durch die kunstreichen Regeln Algebre so gemeincklich die Coss genent werden",
        author = "Christoff Rudolff",
        year = 1525,
        summary = "Rudolff's Coss of 1525 was a German algebra book that wrote the root sign much like the modern square root sign.",
        details = "The book was printed at Strasbourg in 1525 and set out the rules for equations in German for craftsmen and merchants. Accounts differ on whether Rudolff or an earlier writer first set the root sign in print, so the record makes no claim to the first use.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.TOOL,
        section = "Merchants",
        title = "Apian's merchant's reckoning in three books",
        originalTitle = "Ein newe und wolgegruendte underweisung aller Kauffmans Rechnung in dreyen Buechern",
        author = "Peter Apian",
        year = 1527,
        summary = "Apian's merchant's reckoning of 1527 taught the arithmetic of trade in three books, with problems of exchange, partnership and profit worked out for merchants.",
        details = "Apian wrote the book in the years when he took up his mathematics post at Ingolstadt, and it was printed in German for the merchants of the empire. A Frankfurt edition of 1537 is known as well, and the book's problems on money, weights and the sharing of profit are its main subject.",
        rare = true,
    ),

    entry(
        databaseId = 52,
        kind = Kind.TOOL,
        section = "Proportion",
        title = "Duerer's four books of human proportion",
        originalTitle = "Vier Buecher von menschlicher Proportion",
        author = "Albrecht Duerer",
        year = 1528,
        summary = "Duerer's four books of human proportion gave an ideal figure in a canon of heads and rules for drawing the body in its movements.",
        details = "The books appeared at Nuremberg in 1528, after Duerer's death on 6 April of that year, and they set out a canon of eight heads for a standing man. Accounts differ on how closely Duerer's canon followed the ideal figures of earlier artists.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.TOOL,
        section = "Conics",
        title = "Werner's book on the twenty-two conic elements",
        originalTitle = "Libellus super viginti duobus elementis conicis",
        author = "Johannes Werner",
        year = 1522,
        summary = "Werner's book of 1522 treats the conic sections as a set of twenty-two elements and was printed at Nuremberg.",
        details = "The conic treatise was printed at Nuremberg in 1522 by Friedrich Peypus, and the same volume holds commentaries on the doubling of the cube and on a problem of Dionysodorus. Werner died in 1522, the year the book came out, and the bookseller Lucas Alantsee is credited with bringing his geometry into print.",
        rare = true,
    ),

)
