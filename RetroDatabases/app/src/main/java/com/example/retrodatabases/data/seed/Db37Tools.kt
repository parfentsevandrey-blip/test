package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1670 to 1680: a southern star catalogue, a geometry lecture book, an instrument treatise, a chemistry course, a merchant's manual and a historical dictionary. */
val db37Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 37,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Halley's southern star catalogue",
        originalTitle = "Catalogus stellarum australium",
        author = "Edmond Halley",
        year = 1679,
        summary = "Halley's catalogue gives the positions of southern stars that he observed from St Helena in 1677 and 1678, adding the southern sky to the star lists of Europe.",
        details = "Halley published the catalogue in London in 1679 after observing from St Helena in 1677 and 1678. Most star catalogues of the period had been drawn from the northern sky, and this one filled much of the gap.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.TOOL,
        section = "Geometry",
        title = "Barrow's Lectiones geometricae",
        originalTitle = "Lectiones geometricae",
        author = "Isaac Barrow",
        year = 1670,
        summary = "Barrow's Latin lectures on geometry include his characteristic triangle, a small right triangle whose sides give the slope of a curve at a point.",
        details = "Barrow gave the lectures at Cambridge and published them in London in 1670. The work sets out the characteristic triangle for tangents, an early step towards the calculus, and Newton had been Barrow's pupil.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Hevelius's Machina coelestis",
        originalTitle = "Machina coelestis",
        author = "Johannes Hevelius",
        year = 1673,
        summary = "Hevelius's Machina coelestis describes the instruments he used at Danzig for measuring the stars, with their construction and their use.",
        details = "The first part of the book appeared at Danzig in 1673, and a second part followed in 1679. Hevelius preferred open sights to telescopic ones on his instruments, and he argued the point with French observers.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.TOOL,
        section = "Chemistry",
        title = "Lemery's Cours de chymie",
        originalTitle = "Cours de chymie",
        author = "Nicolas Lemery",
        year = 1675,
        summary = "Lemery's Cours de chymie was a practical textbook of chemical work, with the preparation of substances set out step by step for students.",
        details = "Lemery published the course in Paris in 1675, and it became one of the most widely read chemistry texts of its century. It described furnaces and the preparation of salts, and it was translated into several languages.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.TOOL,
        section = "Commerce",
        title = "Savary's Le Parfait negociant",
        originalTitle = "Le Parfait negociant",
        author = "Jacques Savary",
        year = 1675,
        summary = "Savary's manual for the merchant covered commercial arithmetic, bills of exchange and the keeping of accounts for a trading house.",
        details = "Savary published the book in Paris in 1675, and it became a standard guide for merchants in France and abroad. Its tables for exchange and its account of bookkeeping were used for decades after its first appearance.",
        rare = true,
    ),

    entry(
        databaseId = 37,
        kind = Kind.TOOL,
        section = "Dictionaries",
        title = "Moreri's Grand dictionnaire historique",
        originalTitle = "Le grand dictionnaire historique",
        author = "Louis Moreri",
        year = 1674,
        summary = "Moreri's historical dictionary gave short lives of people and accounts of places in alphabetical order, a reference work for readers of the period.",
        details = "Moreri's first edition appeared at Lyon in 1674, and later editions enlarged it with new articles. It was one of the first large French dictionaries of biography and history in alphabetical order.",
    ),

)
