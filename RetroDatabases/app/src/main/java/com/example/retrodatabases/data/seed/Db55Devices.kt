package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1490 to 1500: a globe of the Earth, a Venetian clock, Cyrillic printing at Krakow and Cetinje, a bronze astrolabe from the sea off Oman and Greek types for Venice. */
val db55Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 55,
        kind = Kind.DEVICE,
        section = "Globes",
        title = "Behaim's Erdapfel, the oldest surviving globe",
        originalTitle = "Erdapfel",
        author = "Martin Behaim",
        year = 1492,
        summary = "Behaim's Erdapfel of 1492 is the oldest surviving globe of the Earth, and it shows the known world of its day without the Americas.",
        details = "Behaim made the globe in Nuremberg in 1492, the year Columbus reached the Caribbean, and the globe was finished before the news of that voyage had spread. Its makers relied on the sea-charts and travel reports of their day.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The astronomical clock tower of Venice",
        originalTitle = "Torre dell'Orologio",
        author = "Zuan Paolo and Zuan Carlo Rainieri",
        year = 1499,
        summary = "Venice's clock tower, unveiled on 1 February 1499, shows the hours and the signs of the zodiac on its dial, and two bronze figures strike the bell.",
        details = "Build dates for the tower range from 1496 to 1499 in different accounts, and the Rainieri from Reggio Emilia are credited with the clock, though sources disagree on whether they were father and son or brothers. The record uses 1499, the year of the official unveiling.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.DEVICE,
        section = "Cyrillic type",
        title = "Fiol's Cyrillic types at Krakow",
        originalTitle = "Oktoih",
        author = "Schweipolt Fiol",
        year = 1491,
        summary = "Schweipolt Fiol printed an Octoechos in Cyrillic letters at Krakow in 1491, and that book is credited as the earliest Cyrillic-script book in print.",
        details = "The Krakow printing of 1491 is the usual claim for the earliest Cyrillic printing. Accounts differ on which Cyrillic book comes first, because the Cetinje book of January 1494 is named as the earliest South Slavic one.",
        rare = true,
    ),

    entry(
        databaseId = 55,
        kind = Kind.DEVICE,
        section = "Printing presses",
        title = "The Obod press at Cetinje",
        originalTitle = "Oktoih prvoglasnik",
        author = "Durad Crnojevic",
        year = 1494,
        summary = "The Obod press at Cetinje, in the Montenegrin lands, printed the Octoechos of the First Tone in January 1494, and that book is named as the earliest South Slavic Cyrillic book.",
        details = "Work at Obod began in 1493 and the book was finished in January 1494, the date the record uses. Accounts differ on which Cyrillic printing is the first, since the Krakow Octoechos of 1491 is credited with the earliest Cyrillic-script print.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "A bronze maritime astrolabe from off Oman",
        originalTitle = "Astrolabe",
        author = "Unknown maker; object reported by Mearns",
        year = 1496,
        summary = "A bronze maritime astrolabe found off the coast of Oman was dated by its finder to between 1496 and 1500, and the record uses the start of that estimated range.",
        details = "The disc was found off Oman in 2014 by Mearns, and Warwick researchers settled its identity once scan data brought out its etched markings. The date is an estimate by its finder, not an inscribed date, so the record uses 1496 with the range named here.",
        rare = true,
    ),

    entry(
        databaseId = 55,
        kind = Kind.DEVICE,
        section = "Type",
        title = "Griffo's Greek types for the Aldine press",
        originalTitle = "Greek types of the Aldine press",
        author = "Francesco Griffo",
        year = 1495,
        summary = "Francesco Griffo, a punchcutter, cut the Greek types with which the Aldine press set its early Greek books at Venice.",
        details = "Griffo worked for Aldus Manutius, and the Greek founts he cut were used for the press's early Greek editions. The date given is the year of the first Greek books set in the type.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.DEVICE,
        section = "Woodblocks",
        title = "Woodblocks reused for the towns of the Nuremberg Chronicle",
        originalTitle = "Liber chronicarum",
        author = "Hartmann Schedel",
        year = 1493,
        summary = "The woodblocks cut for Schedel's Nuremberg Chronicle were used again and again, so that one block could stand for several towns under different names.",
        details = "Schedel's chronicle of 1493 printed its city views from a stock of blocks that were reused for different places. The record uses 1493, the year of the first printing, and accounts differ on the exact number of blocks the book used.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.DEVICE,
        section = "Printing presses",
        title = "The d'Ortas press at Leiria",
        originalTitle = "Almanach perpetuum",
        author = "Samuel or Abraham d'Ortas",
        year = 1496,
        summary = "The d'Ortas press at Leiria printed Zacuto's Almanach perpetuum in 1496, a scientific text that moved Portuguese printing beyond religious books.",
        details = "One account dates the Leiria printing to 25 February 1496, and the printer's first name is given as Samuel in some sources and Abraham in others. The Faro Torah of 1487 is usually cited as the earliest Portuguese printed book, so Leiria was not the first press in Portugal.",
    ),

)
