package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1420 to 1430: Al-Kashi's value of pi and his decimal arithmetic, Pizzigano's Antillia chart, Parameshvara's planetary computations, Taccola's engineering book and Al-Kashi's Ladder of Heaven. */
val db62Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 62,
        kind = Kind.TOOL,
        section = "Circles",
        title = "Al-Kashi's value of 2 pi to sixteen places",
        originalTitle = "Risala al-muhitiyya",
        author = "Jamshid al-Kashi",
        year = 1424,
        summary = "Al-Kashi's treatise of 1424 gave 2 pi to sixteen decimal places, a record for its time, computed from polygons with very many sides.",
        details = "He wrote the treatise at Samarkand, and he gave the value in sexagesimal digits as well as in decimals. The method is the polygon method of Archimedes carried much further, and the value held the record for pi for well over a century.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.TOOL,
        section = "Charts",
        title = "Pizzigano's chart of the islands of Antillia",
        originalTitle = "Portolan chart of 1424",
        author = "Zuane Pizzigano",
        year = 1424,
        summary = "Pizzigano's chart of 1424 is an early portolan chart that shows islands in the western Atlantic, among them Antillia.",
        details = "The chart is dated 1424 and signed by Zuane Pizzigano, and it is among the earliest charts to show islands far to the west of Europe. Its place of origin is debated, and the island names are read in different ways by historians.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Al-Kashi's Key to Arithmetic",
        originalTitle = "Miftah al-hisab",
        author = "Jamshid al-Kashi",
        year = 1427,
        summary = "Al-Kashi's Key to Arithmetic of 1427 worked with decimal fractions and set out rules for roots and for the computation of tables.",
        details = "The book was finished at Samarkand in 1427, and it computes with fractions written in decimal places. Stevin's De Thiende of 1585 is usually named as the first European account of decimal fractions, so the Samarkand book is more than 150 years earlier.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.TOOL,
        section = "Planetary computation",
        title = "Parameshvara's planetary computations",
        originalTitle = "Drgganita",
        author = "Parameshvara of the Kerala school",
        year = 1430,
        summary = "Parameshvara's Drgganita of about 1430 sets out a method of computing the positions of the planets from observations, in the Kerala tradition of Indian astronomy.",
        details = "The work is usually dated to about 1430, and the author's dates are known only roughly. It is a little-known treatise outside the Kerala school, and its exact date is disputed among historians of Indian astronomy.",
        rare = true,
    ),

    entry(
        databaseId = 62,
        kind = Kind.TOOL,
        section = "Engineering books",
        title = "Taccola's book of machines",
        originalTitle = "De ingeneis",
        author = "Mariano di Jacopo, called Taccola",
        year = 1425,
        summary = "Taccola's De ingeneis is a Sienese manuscript of machines and engineering drawings, begun in the 1420s and kept in Italian libraries.",
        details = "The drawings show hoists, pumps and water engines, and the book was added to over later decades. The year 1425 is a convention for its start within the decade, and accounts give different dates for the first drawings.",
        rare = true,
    ),

    entry(
        databaseId = 62,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Al-Kashi's Ladder of Heaven",
        originalTitle = "Sullam al-sama",
        author = "Jamshid al-Kashi",
        year = 1420,
        summary = "Al-Kashi's Ladder of Heaven is a treatise on astronomical computation that he wrote in the circle of the Samarkand observatory.",
        details = "The treatise is usually dated to the 1420s, and the year 1420 is used here as a convention for the decade. Its date and its order among Al-Kashi's works are uncertain, so the record makes no claim about its exact year.",
    ),

)
