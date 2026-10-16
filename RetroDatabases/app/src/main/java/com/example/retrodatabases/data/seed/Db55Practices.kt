package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1490 to 1500: double-entry bookkeeping, a printed news letter, a cipher letter from Buda, a humanist script and the sealing of a papal bull. */
val db55Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 55,
        kind = Kind.PRACTICE,
        section = "Accounts",
        title = "Pacioli's double-entry bookkeeping in the Summa",
        originalTitle = "Summa de arithmetica",
        author = "Luca Pacioli",
        year = 1494,
        summary = "Pacioli's Summa de arithmetica of 1494 gave the first printed account of double-entry bookkeeping, in which each sum is entered as a debit in one account and as a credit in another.",
        details = "The account of bookkeeping is a treatise on accounts and records inside the Summa, printed at Venice in November 1494. Accounts differ on how far Pacioli described the method that merchants already used and how far he set out a method of his own.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.PRACTICE,
        section = "News",
        title = "Columbus's printed letter of the discovery, within months",
        originalTitle = "Epistola de insulis nuper inventis",
        author = "Christopher Columbus",
        year = 1493,
        summary = "Columbus's letter reporting his voyage was printed in several cities in 1493, so that news of the Indies reached readers across Europe within months of his return.",
        details = "The letter was printed at Barcelona and at Rome, among other places, in Spanish and in Latin. Accounts differ on which printing came first, and the record uses the year of the first printing.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.PRACTICE,
        section = "Ciphers",
        title = "Cipher letters from Buda filed beside their decoded copies",
        originalTitle = "Extractus litteram in Ziffera Maphei Triviliensis",
        author = "Maffeo da Treviglio, envoy of Ludovico Sforza",
        year = 1490,
        summary = "A letter of April 1490 from Milan's envoy at Buda was written in cipher, and the Milanese court made a decoded copy to file beside the coded original.",
        details = "The coded original survives in the State Archives of Milan with the decoded transcript beside it, and it concerns keeping the peace with the Ottomans. The archive catalogue gives Vienna as the place of writing, although the envoy was posted at Buda from 1490 to 1493, so accounts differ on where the letter was written. The record uses 1490, the year of the letter.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.PRACTICE,
        section = "Scribes",
        title = "Humanist script copied for the Corvinian library",
        originalTitle = "Humanist minuscule",
        author = "Florentine scribes for King Matthias Corvinus",
        year = 1490,
        summary = "The manuscripts copied for King Matthias Corvinus were written in the clear humanist script of the Italian scholars, a hand that the Florentine scribes brought to Buda.",
        details = "The king brought scribes from Florence to copy his books for the library. The record uses 1490, the year of the king's death, after which the library began to decline.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.PRACTICE,
        section = "Papal documents",
        title = "The leaden seal of a papal bull",
        originalTitle = "Bulla",
        author = "The papal chancery",
        year = 1493,
        summary = "A solemn papal bull was closed with a leaden seal, the bulla, hung on cords, and the seal gave the document its authority across Europe.",
        details = "The bull Inter caetera of May 1493 was issued in this form, and the treaty of 1494 that followed drew on its line. The record uses 1493, the year of the bull.",
    ),

)
