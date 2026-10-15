package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1750 to 1760: a dictionary, a type face, lunar tables, a lunar theory, a popular astronomy book and a theory of the heavens. */
val db29Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 29,
        kind = Kind.TOOL,
        section = "Dictionaries",
        title = "Johnson's Dictionary of the English Language",
        originalTitle = "A Dictionary of the English Language",
        author = "Samuel Johnson",
        year = 1755,
        summary = "A two-volume English dictionary that defined words and illustrated them with quotations from English writers.",
        details = "Johnson's dictionary was published in London in 1755 after several years of work. Its definitions were illustrated with quotations, and it remained the standard English dictionary for a century and more.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.TOOL,
        section = "Printing",
        title = "Baskerville's types",
        originalTitle = "Baskerville's Virgil",
        author = "John Baskerville",
        year = 1757,
        summary = "Type faces cut and cast by John Baskerville for his Virgil of 1757, with clean, sharp letters on smooth paper.",
        details = "Baskerville printed his edition of Virgil at Birmingham in 1757 with types of his own design. He used hot-pressed paper, and his faces were admired and widely copied.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Mayer's Tabulae motuum solis et lunae",
        originalTitle = "Tabulae motuum solis et lunae",
        author = "Tobias Mayer",
        year = 1753,
        summary = "A set of tables for the motions of the Sun and Moon, from which navigators could find the Moon's place at sea.",
        details = "Mayer published the tables in 1753, with the theory of lunar motion behind them. The Board of Longitude later paid his widow 3000 pounds for the tables, which made them a standard aid to lunar navigation.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Clairaut's lunar theory of 1752",
        originalTitle = "Theorie de la lune",
        author = "Alexis Clairaut",
        year = 1752,
        summary = "Clairaut's calculation of the Moon's motion with the Sun's pull included, printed in Paris in 1752.",
        details = "The theory treated the Moon's motion with the pull of the Sun taken into account, which the earlier lunar theories had handled poorly. Its results fed the lunar tables that navigators used in the 1750s.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Ferguson's Astronomy Explained",
        originalTitle = "Astronomy Explained upon Sir Isaac Newton's Principles",
        author = "James Ferguson",
        year = 1756,
        summary = "A popular astronomy textbook that explained Newton's principles to general readers with diagrams and little mathematics.",
        details = "Ferguson's book of 1756 was written for readers without advanced mathematics, and it taught the motions of the planets with diagrams and simple arithmetic. It went through many editions and was used in lectures.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.TOOL,
        section = "Cosmology",
        title = "Kant's theory of the heavens",
        originalTitle = "Allgemeine Naturgeschichte und Theorie des Himmels",
        author = "Immanuel Kant",
        year = 1755,
        summary = "Kant's account of how a rotating cloud of matter could settle into the Sun and the planets, published in 1755.",
        details = "Kant described the solar system forming from a rotating cloud of particles, an idea later called the nebular hypothesis. He wrote the work in German in 1755, and it drew on earlier ideas about the heavens.",
    ),

)
