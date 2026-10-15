package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1500 to 1510: the octavo pocket book, a papal licence for printing, a Venetian year, Hindu-Arabic figures in ledgers and the examining of pilots. */
val db54Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 54,
        kind = Kind.PRACTICE,
        section = "Printing",
        title = "Aldus's small octavo, the pocket book",
        originalTitle = "Virgilius in octavo",
        author = "Aldus Manutius",
        year = 1501,
        summary = "Aldus printed small octavo books at Venice from 1501, so that a scholar could carry a classic text in a pocket-sized volume.",
        details = "The small format used the italic letters that Aldus introduced in the same years, and it made texts cheaper and easier to carry. Other printers copied the format, so that small editions of the classics became common.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.PRACTICE,
        section = "Licensing",
        title = "The papal bull on licences for printed books",
        originalTitle = "Inter multiplices",
        author = "Pope Alexander VI",
        year = 1501,
        summary = "The papal bull Inter multiplices of 1501 required a licence from church authorities before a book could be printed, which put the press under the Church's checks.",
        details = "Alexander VI issued the bull in 1501, and it was one of the first papal measures to control the printing of books. Later rules on printing drew on the same idea that a licence should come before the press.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.PRACTICE,
        section = "Calendar",
        title = "Dating by the Venetian year that began on 1 March",
        originalTitle = "More veneto",
        author = "Venetian chancery",
        year = 1500,
        summary = "Venice counted its civil year from 1 March, so a date in January or February belonged to the year before the one shown by the Julian count.",
        details = "The Venetian chancery kept this reckoning for centuries, and public records of the city used it in their dates. A reader of the period has to check which year a date belongs to before comparing it with other records.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.PRACTICE,
        section = "Ledgers",
        title = "Writing sums in Hindu-Arabic figures in merchants' ledgers",
        originalTitle = "Ledger practice",
        author = "Italian merchants",
        year = 1500,
        summary = "By 1500 Italian merchants wrote most of their sums in Hindu-Arabic figures, which made ledger arithmetic simpler than the older notations.",
        details = "Pacioli's book of 1494 on double-entry bookkeeping used these figures throughout, and the practice spread from the Italian cities to northern Europe in the following decades. The change came gradually, so the year 1500 marks a point in a long transition rather than a date of adoption.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.PRACTICE,
        section = "Pilots",
        title = "Examining pilots at Seville in 1508",
        originalTitle = "Piloto mayor",
        author = "Casa de Contratacion",
        year = 1508,
        summary = "In 1508 the Casa de Contratacion at Seville appointed a chief pilot, the piloto mayor, whose duties included examining the pilots who took ships to the Indies.",
        details = "Amerigo Vespucci was named the first pilot major in 1508, and the post also had the duty of keeping the master chart up to date. The examining of pilots became one of the office's regular tasks, and it was a practice of record keeping as much as of seamanship.",
    ),

)
