package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1310 to 1318: a reconciling medical book, Marchetto's second music treatise, Wang Zhen's Nong shu, an anatomy text, an English compendium and a Genoese portolan chart. */
val db73Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 73,
        kind = Kind.TOOL,
        section = "Medicine",
        title = "Pietro d'Abano's Conciliator, a book that reconciles philosophers and physicians",
        originalTitle = "Conciliator differentiarum philosophorum et praecipue medicorum",
        author = "Pietro d'Abano",
        year = 1310,
        summary = "Pietro d'Abano completed his Conciliator at Padua in 1310, a Latin book that sets out the disputed questions of philosophy and medicine and reconciles the views of the authorities.",
        details = "The book was printed in several editions and became a reference for medical students. The inquisition charged Pietro with heresy, and he died in 1315 or 1316 while the charge was still pending, so accounts differ on his death year.",
        rare = true,
    ),

    entry(
        databaseId = 73,
        kind = Kind.TOOL,
        section = "Music",
        title = "Marchetto of Padua's Pomerium, a music treatise of 1318",
        originalTitle = "Pomerium",
        author = "Marchetto of Padua",
        year = 1318,
        summary = "Marchetto of Padua completed his Pomerium in 1318, a Latin treatise on music that follows his earlier Lucidarium and continues its teaching on intervals and notation.",
        details = "The two treatises are read together to study the notation and theory of music in the early fourteenth century. The date 1318 is the one usually given for the Pomerium.",
        rare = true,
    ),

    entry(
        databaseId = 73,
        kind = Kind.TOOL,
        section = "Farming",
        title = "Wang Zhen's Nong shu, a treatise on farming with a chapter on printing",
        originalTitle = "Nong shu",
        author = "Wang Zhen",
        year = 1313,
        summary = "Wang Zhen's Nong shu is a Yuan treatise on farming that also describes printing with wooden movable type and an illustrated register of farm implements and machines.",
        details = "The illustrated register is called the Nongqi tupu, and the 1313 text has not survived, so the surviving copies come from later printings. Accounts differ on the date, with one account putting the book about 1303, and that dispute is noted here.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.TOOL,
        section = "Anatomy",
        title = "Mondino's Anathomia, a teaching text on the parts of the body",
        originalTitle = "Anathomia",
        author = "Mondino de' Luzzi",
        year = 1316,
        summary = "Mondino de' Luzzi completed his Anathomia at Bologna in 1316, a short Latin text on the parts of the body written for teaching from dissection.",
        details = "The book became a standard teaching text in the universities, and its first printing came in the later fifteenth century. Accounts of the dates of his dissections give 1315 or 1316, and the book is dated 1316 here.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.TOOL,
        section = "Medicine",
        title = "John of Gaddesden's Rosa Anglica, an English medical compendium",
        originalTitle = "Rosa Anglica",
        author = "John of Gaddesden",
        year = 1314,
        summary = "John of Gaddesden, an English physician, compiled the Rosa Anglica, a Latin medical compendium written about 1314 that was read in England for generations.",
        details = "Its title means the English rose, and it is arranged by the ailments of the body, from fevers to surgery. The date about 1314 is the date usually given for its composition.",
        rare = true,
    ),

    entry(
        databaseId = 73,
        kind = Kind.TOOL,
        section = "Navigation",
        title = "Pietro Vesconte's portolan chart of 1311, drawn at Genoa",
        originalTitle = "Carta portolana",
        author = "Pietro Vesconte",
        year = 1311,
        summary = "Pietro Vesconte drew a portolan sea chart at Genoa in 1311, marked with rhumb lines that let pilots steer between the ports of the Mediterranean.",
        details = "Portolan charts gave pilots bearings and distances between ports, and Vesconte was one of the Genoese mapmakers who drew them. The date 1311 is the one given for this chart.",
    ),

)
