package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1430 to 1440: Margery Kempe's dictated book, English in Chancery, a verse on trade, Lydgate's Boccaccio, Santillana's Castilian poem and King Duarte's treatise on loyal counsel. */
val db61Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 61,
        kind = Kind.LANGUAGE,
        section = "Autobiography",
        title = "Margery Kempe's book, dictated in English",
        originalTitle = "The Book of Margery Kempe",
        author = "Margery Kempe, with two scribes",
        year = 1436,
        summary = "Margery Kempe dictated her life in English in the later 1430s, and the result is often called the first autobiography written in English.",
        details = "She dictated the book between about 1436 and 1438 to scribes, because she could not write, and it describes her travels and visions. The only surviving manuscript is a copy made later, so the wording of the first dictation is known only through that copy.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.LANGUAGE,
        section = "Chancery",
        title = "The Chancery standard of English in London offices",
        originalTitle = "Chancery English",
        author = "The clerks of the royal Chancery",
        year = 1430,
        summary = "The English written by the royal Chancery in London from about 1430 became a standard spelling and wording that other offices copied.",
        details = "Accounts differ on the exact decade when the Chancery standard took its settled form, and the dates given range from the 1420s to the 1440s. Historians of English use the standard as one root of the written English that printers later spread.",
        rare = true,
    ),

    entry(
        databaseId = 61,
        kind = Kind.LANGUAGE,
        section = "Verse",
        title = "The Libelle of Englyshe Polycye, a verse on English trade",
        originalTitle = "The Libelle of Englyshe Polycye",
        author = "An anonymous English writer",
        year = 1436,
        summary = "The Libelle of 1436 is an English verse that discusses English trade and sea power, and it is one of the early political poems written in English.",
        details = "The poem is usually dated 1436, and its author is unknown. Its surviving copies come from later manuscripts, so the exact wording differs from one copy to another.",
        rare = true,
    ),

    entry(
        databaseId = 61,
        kind = Kind.LANGUAGE,
        section = "Translation",
        title = "Lydgate's Fall of Princes, Boccaccio in English verse",
        originalTitle = "The Fall of Princes",
        author = "John Lydgate",
        year = 1431,
        summary = "Lydgate's Fall of Princes turned Boccaccio's tales of ruin into English verse, and it was made in the 1430s for a noble patron.",
        details = "The translation was begun in the early 1430s, and accounts give dates between about 1431 and 1438 for its completion. It was made for Humphrey, Duke of Gloucester, and it is one of the long English works that show the language being used for learned translation.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.LANGUAGE,
        section = "Poetry",
        title = "Santillana's Comedieta de Ponza, a Castilian poem on a naval battle",
        originalTitle = "Comedieta de Ponza",
        author = "Inigo Lopez de Mendoza, Marquis of Santillana",
        year = 1436,
        summary = "Santillana's Comedieta de Ponza of about 1436 is a Castilian allegorical poem that treats the naval battle of Ponza of 1435 in verse.",
        details = "At Ponza in 1435 a Genoese and Milanese fleet defeated the fleet of Alfonso V of Aragon, and the king was taken prisoner. The usual date for the poem is about 1436, a year after the battle.",
        rare = true,
    ),

    entry(
        databaseId = 61,
        kind = Kind.LANGUAGE,
        section = "Moral prose",
        title = "Duarte's Leal Conselheiro, a treatise on loyalty and good counsel",
        originalTitle = "O Leal Conselheiro",
        author = "King Duarte of Portugal",
        year = 1437,
        summary = "The Leal Conselheiro is a Portuguese treatise on loyalty and good counsel, written by King Duarte of Portugal toward the end of his reign.",
        details = "Accounts place the treatise toward the close of Duarte's reign, which ended with his death in 1438. The Paris codex that holds his riding manual also holds this work.",
    ),

)
