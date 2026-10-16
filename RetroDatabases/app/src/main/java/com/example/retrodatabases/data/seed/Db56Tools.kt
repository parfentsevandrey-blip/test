package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1480 to 1490: the Ulm Ptolemy with its woodcut maps, Vitruvius, Euclid, Alberti, the Alfonsine tables and Widmann's arithmetic. */
val db56Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 56,
        kind = Kind.TOOL,
        section = "Geography",
        title = "The Ulm Ptolemy with woodcut maps",
        originalTitle = "Cosmographia",
        author = "Claudius Ptolemy, printed by Lienhart Holle",
        year = 1482,
        summary = "The Ulm edition of Ptolemy's Geography, printed by Lienhart Holle on 16 July 1482, carries thirty-two woodcut maps, and it is the first atlas of its kind printed in Germany.",
        details = "Holle used woodblocks where the earlier printings of 1477 and 1478 had copper engravings, and the woodcutter Johannes Schnitzer of Arnsheim cut the world map. The edition was a commercial loss for Holle, who had shut his press by 1484, and his type and blocks passed to Johann Reger.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.TOOL,
        section = "Architecture",
        title = "Vitruvius's first printed edition at Rome",
        originalTitle = "De architectura",
        author = "Vitruvius, edited by Giovanni Sulpizio da Veroli",
        year = 1486,
        summary = "The first printed edition of Vitruvius's ten books on architecture appeared at Rome in 1486, and it gave the building trade its classical handbook in print.",
        details = "The text was edited by Giovanni Sulpizio da Veroli and printed at Rome, and it had no drawings, so builders read the descriptions of columns, machines and water supply without plates. Accounts differ on the exact printing date within 1486 and 1487.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "The Alfonsine tables printed at Venice",
        originalTitle = "Tabulae astronomicae Alphonsi",
        author = "Alfonso X of Castile, with the Toledo compilers",
        year = 1483,
        summary = "The Alfonsine tables of planetary positions, first made in thirteenth-century Castile, were printed at Venice in 1483, and astronomers used the printed volume for the next century.",
        details = "The tables were compiled under Alfonso X of Castile and printed at Venice in 1483, with a printed preface that set out their use. Dates given for the Venice printing vary between 1483 and 1484 in different accounts.",
        rare = true,
    ),

    entry(
        databaseId = 56,
        kind = Kind.TOOL,
        section = "Geometry",
        title = "Ratdolt's printed Euclid of Venice",
        originalTitle = "Elementa geometriae",
        author = "Euclid, translated by Campanus of Novara, printed by Erhard Ratdolt",
        year = 1482,
        summary = "Ratdolt's Venetian edition of Euclid's Elements, dated 1482, is the first printed Euclid, with its figures set in the text beside the proofs.",
        details = "Campanus of Novara made the Latin translation that Ratdolt printed in 1482, and the figures were printed as part of the text instead of being drawn by hand. Its date is usually given as 1482, and the book is the earliest printed text of the Elements.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.TOOL,
        section = "Architecture",
        title = "Alberti's treatise on building, printed at Florence",
        originalTitle = "De re aedificatoria",
        author = "Leon Battista Alberti",
        year = 1485,
        summary = "Alberti's ten books on building were printed at Florence in 1485, and it was the first printed architectural treatise written by a Renaissance author rather than a classical one.",
        details = "The treatise was written in Latin in the 1450s and printed in Florence in 1485, and it set out the orders, walls and public works in a form that could be used on a building site. Accounts differ on whether the Florentine printing or a later Latin text should be called the first edition.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Widmann's arithmetic with the first printed plus and minus",
        originalTitle = "Behende und hubsche Rechenung auff allen Kauffmanschafft",
        author = "Johannes Widmann",
        year = 1489,
        summary = "Widmann's commercial arithmetic printed at Leipzig in 1489 uses the plus and minus signs in print, and it is the earliest printed book with those signs.",
        details = "The book was printed at Leipzig by Konrad Kachelofen in 1489, and in it the signs mark gains and shortfalls in business problems, such as surplus and deficit weights of bales and boxes. Their role as general operators came later, and the origin of the signs is still debated.",
    ),

)
