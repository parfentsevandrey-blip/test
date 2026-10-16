package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1320 to 1330: a treatise on proportions, a geography with coordinates, a sea chart in a crusade book, a Hebrew arithmetic, a notation treatise and a geometry treatise. */
val db72Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 72,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "Bradwardine's Treatise on Proportions, written at Oxford in 1328",
        originalTitle = "Tractatus de proportionibus",
        author = "Thomas Bradwardine",
        year = 1328,
        summary = "Thomas Bradwardine wrote his Treatise on Proportions in 1328, a method for relating speed, power and resistance by the arithmetic of ratios.",
        details = "The treatise was written at Oxford, and it set out the rule that ties the speed of a moving body to the ratio of its power to the resistance it meets. The year 1328 is the date usually given, and the rule was debated by later writers at Oxford and Paris.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.TOOL,
        section = "Geography",
        title = "Abu'l-Fida's Taqwim al-buldan, a geography with the coordinates of places",
        originalTitle = "Taqwim al-buldan",
        author = "Abu'l-Fida",
        year = 1321,
        summary = "Abu'l-Fida completed the Taqwim al-buldan in 1321, an Arabic geography that lists the latitudes and longitudes of many cities for travellers and scholars.",
        details = "The author was a prince of Hama in Syria, and the book gathers the places of the Islamic world with coordinates arranged in tables. The year 1321 is the completion date usually given.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.TOOL,
        section = "Maps",
        title = "Sanudo's Book of the Secrets with Vesconte's world map, 1321",
        originalTitle = "Liber secretorum fidelium crucis",
        author = "Marino Sanudo and Pietro Vesconte",
        year = 1321,
        summary = "Marino Sanudo completed the Book of the Secrets of the Faithful of the Cross in 1321, a plan for a crusade illustrated with maps by the Genoese mapmaker Pietro Vesconte.",
        details = "The book was written to persuade the papacy to mount a crusade and was illustrated with maps. The year 1321 is the completion date usually given, and the maps are the work of Vesconte.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.TOOL,
        section = "Notation",
        title = "The Ars nova, a treatise on new ways of dividing notes",
        originalTitle = "Ars nova",
        author = "Philippe de Vitry",
        year = 1322,
        summary = "The treatise known as the Ars nova, linked with Philippe de Vitry, set out new ways of dividing the notes of music, and it is dated about 1322.",
        details = "The new method allowed notes to be divided in two or in three, and the terms perfect and imperfect were used for the two kinds of division. Accounts differ on the exact year, which is given as about 1320 to 1323, so 1322 is the round date.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Gersonides's Work of the Calculator, a Hebrew book of arithmetic, 1321",
        originalTitle = "Maaseh Hoshev",
        author = "Levi ben Gerson",
        year = 1321,
        summary = "Levi ben Gerson completed the Maaseh Hoshev in 1321, a Hebrew handbook of the operations of arithmetic written for readers of the period.",
        details = "The book sets out the rules for the ordinary operations of arithmetic, with worked examples in the same Hebrew text. The year 1321 is the completion date usually given.",
        rare = true,
    ),

    entry(
        databaseId = 72,
        kind = Kind.TOOL,
        section = "Geometry",
        title = "Bradwardine's Speculative Geometry, a Latin treatise of about 1325",
        originalTitle = "Geometria speculativa",
        author = "Thomas Bradwardine",
        year = 1325,
        summary = "Thomas Bradwardine wrote the Geometria speculativa, a Latin treatise of geometry that sets out proofs about lines and figures, in the years about 1320 to 1330.",
        details = "The date of this treatise is disputed, and accounts place it in the years about 1320 to 1330, when Bradwardine was a scholar at Oxford. The year 1325 is given as a middle date for the range.",
        rare = true,
    ),

)
