package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1380 to 1390: the Ming calendar of 1384, a French treatise on war, a French hunting book and an English cookery book. */
val db66Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 66,
        kind = Kind.TOOL,
        section = "Calendars",
        title = "The Datong calendar of the Ming, issued in 1384",
        originalTitle = "Datong li",
        author = "Ming court astronomers",
        year = 1384,
        summary = "The Datong calendar was issued by the Ming court in 1384 as its official calendar and was based largely on the calendar of the Yuan dynasty.",
        details = "The calendar gave the positions of the sun and the moon for each year, and its years carry the names of the sixty-year cycle. The year 1384 is a jiazi year, the first name of the cycle.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.TOOL,
        section = "Warfare",
        title = "Honore Bovet's Arbre des batailles, a French treatise on war",
        originalTitle = "L'Arbre des batailles",
        author = "Honore Bovet",
        year = 1387,
        summary = "Honore Bovet's Arbre des batailles is a French treatise on the law of war, written between 1387 and 1389 and set out as a tree of questions.",
        details = "Bovet was prior of Salon in Provence, and the book was written for readers among the nobility. The usual dates of composition are 1387 to 1389.",
        rare = true,
    ),

    entry(
        databaseId = 66,
        kind = Kind.TOOL,
        section = "Hunting",
        title = "Gaston Phoebus's Livre de la chasse, a French hunting manual",
        originalTitle = "Livre de la chasse",
        author = "Gaston III, Count of Foix",
        year = 1388,
        summary = "Gaston Phoebus, Count of Foix, wrote his Livre de la chasse about 1387 to 1389 as a manual of hunting in French.",
        details = "The book covers the hunting of deer, boar, bear and wolf, and it is known from many illustrated copies. The usual dates of composition are 1387 to 1389, and the year 1388 is the one used here.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.TOOL,
        section = "Cookery",
        title = "The Forme of Cury, the cookery book of Richard II's kitchen",
        originalTitle = "The Forme of Cury",
        author = "The master cooks of Richard II",
        year = 1390,
        summary = "The Forme of Cury is an English cookery book compiled in 1390 by the master cooks of King Richard II's household.",
        details = "It gives recipes in English for roasts, pottages and sauces, and it is one of the earliest surviving English cookery books. The date 1390 is the usual date of compilation.",
    ),

)
