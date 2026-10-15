package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1550 to 1560: a memory theatre, a numbered Greek Testament, a printing house, a stationers' register and a list of forbidden books. */
val db49Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 49,
        kind = Kind.SYSTEM,
        section = "Memory",
        title = "Camillo's memory theatre, L'idea del theatro",
        originalTitle = "L'idea del theatro",
        author = "Giulio Camillo",
        year = 1550,
        summary = "Camillo's memory theatre, described in a book of 1550, placed images in a built order so that a speaker could recall the whole of learning.",
        details = "The book was printed in Florence in 1550, after the author's death in 1544, and accounts differ on whether a wooden model of the theatre was ever built. Its images and places followed the classical method of memory palaces, adapted to a fixed arrangement of learning.",
        rare = true,
    ),

    entry(
        databaseId = 49,
        kind = Kind.SYSTEM,
        section = "Citation",
        title = "Estienne's numbered verses in the Greek New Testament",
        originalTitle = "Novum Testamentum graece",
        author = "Robert Estienne",
        year = 1551,
        summary = "Robert Estienne's Greek New Testament of 1551 numbered every verse within each chapter, a citation system that later Bibles copied.",
        details = "The edition appeared in 1551, and Estienne's son later told how his father worked out the numbering on horseback during a journey. Accounts differ on whether that journey went to Geneva or to Lyon, and the 1550 Editio Regia had already shown his critical care.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.SYSTEM,
        section = "Printing",
        title = "Plantin's printing house in Antwerp",
        originalTitle = "Officina Plantiniana",
        author = "Christophe Plantin",
        year = 1555,
        summary = "Christophe Plantin began printing at Antwerp in 1555, and his house grew into one of the largest printing businesses of sixteenth-century Europe.",
        details = "Plantin had trained as a bookbinder before he set up as a printer, and the firm later took the name Officina Plantiniana. Its books were sold across Europe, and the house survives in Antwerp as a museum of the printing trade.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.SYSTEM,
        section = "Printing",
        title = "The Stationers' Company charter and its register",
        originalTitle = "Royal charter of the Stationers' Company",
        author = "The Crown, under Queen Mary I",
        year = 1557,
        summary = "The royal charter of 1557 gave the London Stationers' Company control over printing and a register in which books were entered.",
        details = "The first royal grant of incorporation is dated 4 May 1557. Accounts differ on the date of the first register entries, since one archive catalogue starts the copy-entry books in 1557 and a transcript of the registers covers 1554 to 1640.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.SYSTEM,
        section = "Censorship",
        title = "The Pauline Index of prohibited books",
        originalTitle = "Index librorum prohibitorum",
        author = "Pope Paul IV",
        year = 1559,
        summary = "The Pauline Index of 1559 listed the books that Catholics were forbidden to read, and it was issued in Rome under Pope Paul IV.",
        details = "The list was withdrawn later in the same year, after the pope's death, and the Tridentine index of 1564 replaced it. It was printed as a list of titles that booksellers and readers in the Catholic lands had to consult.",
    ),

)
