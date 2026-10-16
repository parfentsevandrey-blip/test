package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1450 to 1460: Gutenberg's screw press, the adjustable hand mould, early Hangeul metal type, the caravel, Orban's great gun, the chain of the Golden Horn, a compartmented type case and Nicholas of Cusa's concave lens. */
val db59Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 59,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Gutenberg's screw press of about 1450",
        originalTitle = "Presse",
        author = "Johann Gutenberg",
        year = 1450,
        summary = "Gutenberg's press of about 1450 pressed inked type against paper with a wooden screw, an arrangement often said to be taken from the wine presses of the Rhineland.",
        details = "The press is generally dated to about 1450, and the account of its design rests on later descriptions and on the surviving books. Accounts differ on how much of the machine was new and how much was borrowed from existing presses, so the claim that it was wholly original is disputed.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "The adjustable hand mould for casting type",
        originalTitle = "Handgiessinstrument",
        author = "Johann Gutenberg, or the founders of his shop",
        year = 1455,
        summary = "The adjustable hand mould let a founder cast type of different body widths from one set of matrices by moving a sliding block.",
        details = "Many writers credit the mould to Gutenberg, but accounts differ on who designed it, and the year 1455 used here is an estimate. Later type founders used hand moulds of this kind.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.DEVICE,
        section = "Korean type",
        title = "Early Hangeul metal type, dated by estimates to 1455 to 1461",
        originalTitle = "Hangeul metal type",
        author = "The court of King Sejo of Joseon",
        year = 1455,
        summary = "Hangeul letters were cast in metal under King Sejo, and the oldest surviving blocks of such type are dated by estimates to between 1455 and 1461.",
        details = "The National Museum of Korea holds about 30 blocks of Hangeul type dated to around 1455, and a Kyungpook National University professor dates a set of excavated Hangeul pieces to 1455 to 1461 from their spelling. A reported find of about 1,600 metal pieces, some 600 of them Hangeul, would be the earliest known if confirmed, and accounts differ on the site of the find.",
        rare = true,
    ),

    entry(
        databaseId = 59,
        kind = Kind.DEVICE,
        section = "Ships",
        title = "The caravel of the African coast",
        originalTitle = "Caravela",
        author = "Portuguese shipwrights",
        year = 1450,
        summary = "The caravel was a light ship with lateen sails, and Portuguese sailors used it along the African coast in the 1450s because it could work against the wind.",
        details = "The caravel's origins are disputed, and accounts give different dates for its first use on the African coast. The lateen sail it carried was a triangle hung from a long yard, which is why the sail's shape is easy to calculate.",
        rare = true,
    ),

    entry(
        databaseId = 59,
        kind = Kind.DEVICE,
        section = "Ordnance",
        title = "Orban's great bombard for the siege of Constantinople",
        originalTitle = "Basilica bombard",
        author = "The founder Orban, for Sultan Mehmed II",
        year = 1453,
        summary = "Orban's great bombard of 1453 threw stone shot at the walls of Constantinople, and it was one of the largest guns of its day.",
        details = "The gun was cast for Mehmed II for the siege that ended with the fall of Constantinople in 1453. Accounts differ on its length, its weight and the size of its stone shot, so none of the figures often given should be taken as exact.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.DEVICE,
        section = "Siege barriers",
        title = "The chain across the Golden Horn in the siege of 1453",
        originalTitle = "Halysis",
        author = "The Byzantine defenders of Constantinople",
        year = 1453,
        summary = "The Byzantines stretched a chain across the entrance to the Golden Horn, and in April 1453 the Ottoman fleet was hauled overland to reach the inner harbour.",
        details = "The chain barred the inner harbour from the sea, and the Ottomans bypassed it rather than breaking it. The city fell on 29 May 1453, after a siege that had begun on 6 April.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.DEVICE,
        section = "Printing shop",
        title = "The compartmented type case of the early press",
        originalTitle = "Kasten",
        author = "The compositors of the early press",
        year = 1450,
        summary = "Early printers kept their sorts in a wooden case divided into compartments, one for each letter and sort, so that a compositor could reach each piece by its place.",
        details = "Later printing manuals describe cases of this kind, and accounts differ on when the compartments first came into use in printing shops. The year 1450 used here marks the start of the decade and is not the date of any dated case.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Cusanus's concave lens for short sight in De beryllo",
        originalTitle = "De beryllo",
        author = "Nicholas of Cusa",
        year = 1458,
        summary = "Nicholas of Cusa's treatise De beryllo of 1458 describes a concave lens that helps a short-sighted reader see clearly, an early European account of a corrective lens.",
        details = "The treatise is dated to 1458 and takes its name from the beryl, a transparent stone. The claim that Cusanus was the first European to describe a concave lens for short sight is commonly made, and it is given here as a claim rather than as settled fact.",
    ),

)
