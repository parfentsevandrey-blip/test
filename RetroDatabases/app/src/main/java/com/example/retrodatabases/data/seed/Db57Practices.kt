package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1470 to 1480: blank spaces for initials, catchwords, errata, Florentine family books and Cicco Simonetta's rules for breaking ciphers. */
val db57Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 57,
        kind = Kind.PRACTICE,
        section = "Book production",
        title = "Leaving spaces for hand-painted initials in printed books",
        originalTitle = "Rubrication",
        author = "Printers and rubricators",
        year = 1470,
        summary = "Early printers often left spaces for capital letters, which were painted or drawn in by hand after the sheets were printed.",
        details = "The custom ran through the 1470s and beyond, and it explains why some early books have blank spaces where initials should stand. The spaces show that printing and hand decoration were separate steps in the workshop.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.PRACTICE,
        section = "Book production",
        title = "Catchwords at the foot of the page",
        originalTitle = "Catchwords",
        author = "Early printers",
        year = 1473,
        summary = "A catchword, the first word of the next page printed at the foot of the page, helped binders and readers keep the leaves in order.",
        details = "The device appears in some printed books of the 1470s, and later printers also numbered their signatures. Not every printer used catchwords, so the record describes a practice rather than one book.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.PRACTICE,
        section = "Family records",
        title = "Florentine family record books, the ricordanze",
        originalTitle = "Ricordanze",
        author = "Florentine households",
        year = 1480,
        summary = "Florentine families kept record books, called ricordanze, where births, deaths, debts and purchases were noted year by year.",
        details = "The books were kept in the family's own hand and served as a record of property as well as a family history. The practice ran through the 1400s and into the 1480s, so the record gives the period rather than one volume.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.PRACTICE,
        section = "Ciphers",
        title = "Cicco Simonetta's thirteen rules for breaking secret writing",
        originalTitle = "Les regles de Cicco Simonetta pour le dechiffrement des ecritures secretes",
        author = "Cicco Simonetta, attributed",
        year = 1474,
        summary = "Cicco Simonetta, a secretary of the Sforza court in Milan, is credited with thirteen rules of 1474 for reading a secret writing without its key, based on letter frequency and word patterns.",
        details = "The treatise is dated to 1474 in the reference accounts, and its rules work with letter frequency and word patterns in Latin and Italian. The text is only attributed to Simonetta, the title given is that of a French edition, and no record shows the rules being applied in the field.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.PRACTICE,
        section = "Book production",
        title = "Errata lists at the end of a printed book",
        originalTitle = "Errata",
        author = "Early printers",
        year = 1478,
        summary = "An errata list, printed at the end of a book, shows that printers corrected the wrong words of a book they had already printed.",
        details = "Such lists are found in some books of the late 1400s, and each entry names the wrong word and gives its correction. The record names no particular book, so its year marks the practice rather than one volume.",
    ),

)
