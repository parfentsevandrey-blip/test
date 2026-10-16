package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1300 to 1310: a Chinese algebra of four unknowns, a medical textbook of Montpellier, a surgical book, two charts of about 1300 and Llull's short art. */
val db74Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 74,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Zhu Shijie's Jade Mirror of the Four Unknowns, a Chinese algebra",
        originalTitle = "Siyuan yujian",
        author = "Zhu Shijie",
        year = 1303,
        summary = "Zhu Shijie's Siyuan yujian of 1303 is a Chinese algebra that handles equations in up to four unknowns, each called an element.",
        details = "The book sets out the coefficients of its equations in diagrams, and the year 1303 is the date usually given for it. Its four-unknown method is the best-known part of the work.",
        rare = true,
    ),

    entry(
        databaseId = 74,
        kind = Kind.TOOL,
        section = "Medicine",
        title = "Bernard de Gordon's Lilium medicinae, a medical textbook of Montpellier",
        originalTitle = "Lilium medicinae",
        author = "Bernard de Gordon",
        year = 1305,
        summary = "Bernard de Gordon completed the Lilium medicinae in 1305, a textbook of medical practice written by a teacher at Montpellier.",
        details = "The book was used as a teaching text for physicians and is arranged by diseases and their treatment. The year 1305 is the usual date given for its completion.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.TOOL,
        section = "Surgery",
        title = "Henri de Mondeville's Chirurgia, a surgical book begun in 1306",
        originalTitle = "Chirurgia",
        author = "Henri de Mondeville",
        year = 1306,
        summary = "Henri de Mondeville, surgeon to the French king, began his Chirurgia in 1306, a surgical handbook that he kept revising until about 1320.",
        details = "The book was revised over more than a decade, and its dates are usually given as 1306 to 1320, so the final form is approximate.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.TOOL,
        section = "Maps",
        title = "The Hereford Mappa Mundi, a world map of about 1300",
        originalTitle = "Mappa mundi",
        author = "No known author",
        year = 1300,
        summary = "The Hereford Mappa Mundi is a medieval world map drawn on one large sheet of vellum, usually dated to about 1300, with Jerusalem at its centre.",
        details = "The map shows the known world with cities, peoples and marvels, and it is kept in Hereford Cathedral. Its date is judged from style and content, so 1300 is an approximate date rather than a recorded one.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.TOOL,
        section = "Charts",
        title = "The Pisan portolan, a sea drawing for Mediterranean sailors",
        originalTitle = "Carta Pisana",
        author = "Unknown Pisan draughtsmen",
        year = 1300,
        summary = "The Carta Pisana is one of the oldest surviving portolan charts, drawn for sailors of the Mediterranean and usually dated between about 1275 and 1300.",
        details = "A portolan chart shows coasts, ports and sailing lines rather than the whole world. The dating range rests on style and place names, so 1300 is the end of the range used here.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.TOOL,
        section = "Logic",
        title = "Llull's Ars brevis, the short book of his combinatory art",
        originalTitle = "Ars brevis",
        author = "Ramon Llull",
        year = 1308,
        summary = "Llull's Ars brevis is a short Latin book that teaches his combinatory art in compact form, usually dated to 1308.",
        details = "It presents the method that the wheel figures of the longer Ars generalis ultima make visible, so readers could apply it without the full apparatus. The date 1308 is the one usually given, but the dates of Llull's several versions of the art overlap.",
    ),

)
