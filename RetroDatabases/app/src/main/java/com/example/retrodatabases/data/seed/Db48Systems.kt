package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1560 to 1570: the Fugger news letters, Gresham's Royal Exchange, two academies, and the Escorial. */
val db48Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 48,
        kind = Kind.SYSTEM,
        section = "News",
        title = "The Fugger news letters from 1568",
        originalTitle = "Fuggerzeitungen",
        author = "The Fugger house of Augsburg",
        year = 1568,
        summary = "The Fugger news letters were handwritten reports of political news sent to the Augsburg banking house from courts across Europe.",
        details = "The series began in 1568 and was kept with the Fugger papers, where later readers found it. The letters travelled by post, and the numbering continued for decades, so the series works as a system of news rather than as a single document.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.SYSTEM,
        section = "Commerce",
        title = "Gresham's Royal Exchange in London",
        originalTitle = "The Royal Exchange",
        author = "Thomas Gresham",
        year = 1566,
        summary = "Gresham's Royal Exchange was a covered meeting place for London merchants, and building on it began in 1566.",
        details = "Gresham planned the exchange in the mid-1560s, and the start of the building is commonly dated to 1566. Elizabeth I opened it in 1571, although some accounts give 1570 as the opening year.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "The Accademia del Disegno in Florence",
        originalTitle = "Accademia delle Arti del Disegno",
        author = "Giorgio Vasari and Cosimo I de' Medici",
        year = 1563,
        summary = "The Accademia del Disegno, founded in Florence in 1563, gathered painters, sculptors and architects under the patronage of the Medici duke.",
        details = "Vasari and Cosimo I de' Medici set it up in 1563, and it is often named as the first formal academy of the visual arts. Accounts differ on whether it was a school, a guild or a court society, so the record uses its Italian name.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "Porta's Academy of Secrets in Naples",
        originalTitle = "Academia Secretorum Naturae",
        author = "Giambattista della Porta",
        year = 1560,
        summary = "Porta's academy in Naples, founded about 1560, met to test claims about nature, and church authorities closed it in 1578.",
        details = "Porta gathered a small group of learned friends in his house in Naples to repeat and check experiments. Accounts give the founding year in different forms, while the papal order that closed the academy in 1578 is the firm date.",
        rare = true,
    ),

    entry(
        databaseId = 48,
        kind = Kind.SYSTEM,
        section = "Royal works",
        title = "The Escorial, the king's monastery and library",
        originalTitle = "El Escorial",
        author = "Philip II of Spain",
        year = 1563,
        summary = "The Escorial, a monastery, palace and library in the hills north-west of Madrid, was begun in 1563 for Philip II.",
        details = "Work started in 1563 and the building was finished in 1584, with a library among its rooms. Architects changed over the years, and accounts differ on the exact date of the first stone, which is often given as April 1563.",
    ),

)
