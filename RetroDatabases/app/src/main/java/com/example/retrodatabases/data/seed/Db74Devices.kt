package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1300 to 1310: spectacles in Florence, Llull's rotating figures, Edward I's trebuchet at Stirling, the Canon Musique clock at Beauvais and the spinning wheel. */
val db74Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 74,
        kind = Kind.DEVICE,
        section = "Glass",
        title = "Spectacles in Italy, first described in a Florentine sermon of 1306",
        originalTitle = "Occhiali",
        author = "Giordano da Pisa",
        year = 1306,
        summary = "Giordano da Pisa, preaching in Florence, remarked that the art of making spectacles was not yet twenty years old, which dates the start of the craft to the twenty years before his sermon.",
        details = "The sermon is dated 23 February 1305 in Florentine reckoning, which began the year on 25 March, and so falls in 1306 in modern reckoning. The text survives only in a record made by a later compiler, so the exact wording is not certain.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.DEVICE,
        section = "Logic",
        title = "Llull's combinatorial figures with rotating circles in the Ars generalis ultima",
        originalTitle = "Ars generalis ultima",
        author = "Ramon Llull",
        year = 1308,
        summary = "Ramon Llull's Ars generalis ultima uses figures of concentric circles marked with letters, so that turning the circles brings different concepts together.",
        details = "The figures set out combinations of Llull's basic concepts, which he labelled with nine letters, and later readers such as Leibniz treated them as a kind of logical machine. The dates usually given for the work run from 1305 to 1308, and 1308 is used here as its completion.",
        rare = true,
    ),

    entry(
        databaseId = 74,
        kind = Kind.DEVICE,
        section = "Siege",
        title = "Edward I's trebuchet, the Warwolf, at Stirling in 1304",
        originalTitle = "Warwolf",
        author = "Edward I of England",
        year = 1304,
        summary = "Edward I had a very large trebuchet called the Warwolf built for the siege of Stirling Castle in 1304, and modern writers often count it among the largest trebuchets ever built.",
        details = "The garrison under Sir William Oliphant held out from about early April to late July 1304. A chronicle describes a single stone breaking through two walls, but accounts differ on whether the engine ended the siege, since some say the garrison had surrendered before it was finished.",
        rare = true,
    ),

    entry(
        databaseId = 74,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The Canon Musique clock at Beauvais, core work from the early 1300s",
        originalTitle = "Horloge astronomique de Beauvais",
        author = "The Beauvais chapter, perhaps Etienne Musique",
        year = 1300,
        summary = "The Canon Musique clock at Beauvais Cathedral has workings that the cathedral dates to the early 1300s, although its first written trace is much later.",
        details = "The early-1300s date comes only from the cathedral's own dating, and the earliest written trace is a record of repairs in 1387 handled by Pierre de Saint-Omer. Later writings from 1540 mention the clock, the donor is named only tentatively, and the wooden case and painted dial are later additions, so 1300 is an approximate date for the core.",
        rare = true,
    ),

    entry(
        databaseId = 74,
        kind = Kind.DEVICE,
        section = "Textiles",
        title = "The spinning wheel in medieval Europe, first recorded in the late 1200s",
        originalTitle = "Spinnrad",
        author = "No single author",
        year = 1300,
        summary = "The spinning wheel, a driven wheel that draws out thread from fibre, reached Europe in the late 1200s, and its first European records are placed in Italy and the German lands.",
        details = "Accounts differ on which early European reference comes first, so 1300 is used here as a round date for the period. The wheel turns the bobbin through a drive band, so the ratio of the two diameters sets how fast the thread is wound.",
    ),

)
