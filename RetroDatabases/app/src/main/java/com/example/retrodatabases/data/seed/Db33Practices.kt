package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1710 to 1720: the Royal Society's priority report, smallpox inoculation, the Statute of Anne, the Marseille cordon and the Stamp Act. */
val db33Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 33,
        kind = Kind.PRACTICE,
        section = "Priority",
        title = "The Royal Society's Commercium epistolicum",
        originalTitle = "Commercium epistolicum D. Johannis Collins et aliorum de analysi promota",
        author = "Royal Society committee, with Isaac Newton",
        year = 1712,
        summary = "A Royal Society report that judged Newton to have had the method of fluxions first, and printed the letters on which it relied.",
        details = "The report was printed by the Royal Society in 1712, and it used letters exchanged by Collins and others to support Newton's priority over Leibniz. Accounts differ on how much of the report Newton wrote himself, and Leibniz complained that the judgement was unfair.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.PRACTICE,
        section = "Medicine",
        title = "Timoni's account of smallpox inoculation",
        originalTitle = "Smallpox inoculation at Constantinople",
        author = "Emanuel Timoni",
        year = 1714,
        summary = "A letter from Constantinople that described the practice of inoculating against smallpox, which reached English readers in 1714.",
        details = "Timoni's letter of December 1713 was forwarded to the Royal Society by John Woodward, and it appeared in the Philosophical Transactions in 1714, although one bibliography gives the dates as 1714 to 1716. The procedure cut the skin and placed fluid taken from a mild case, and accounts differ on his birthplace, since one modern account calls him Italian and another a Greek from Chios.",
        rare = true,
    ),

    entry(
        databaseId = 33,
        kind = Kind.PRACTICE,
        section = "Copyright",
        title = "The Statute of Anne",
        originalTitle = "An Act for the Encouragement of Learning",
        author = "Parliament of Great Britain",
        year = 1710,
        summary = "The first copyright law, which gave authors fourteen years of protection from publication and a second fourteen if they were still living.",
        details = "Parliament passed the Act in 1709, and it took effect in April 1710, which is why the year used here is 1710. Its term could be renewed once for another fourteen years if the author was living at the end of the first term.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.PRACTICE,
        section = "Public health",
        title = "Marseille's cordon of 1720",
        originalTitle = "Cordon sanitaire of Provence",
        author = "Authorities of Provence and Marseille",
        year = 1720,
        summary = "A cordon of troops and checkpoints that cut off Marseille and the land around it during the plague of 1720.",
        details = "The plague reached Marseille by ship in 1720, and the authorities closed the port and guarded the land boundary to stop the disease spreading through Provence. Ships and goods were held in quarantine for forty days, and the epidemic lasted into 1722.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.PRACTICE,
        section = "Taxation",
        title = "The Stamp Act of 1712",
        originalTitle = "Stamp Act of 1712",
        author = "Parliament of Great Britain",
        year = 1712,
        summary = "A tax on newspapers, pamphlets and advertisements, paid by stamping each sheet, which made printed news dearer.",
        details = "Parliament imposed the stamp duty in 1712, and printers resisted it. The duty stayed in force for more than a century and was paid on each stamped sheet.",
    ),
)
