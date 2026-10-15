package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Reference tools of 1890 to 1900: a library classification, mathematical tables and nomograms, and the reference works of the decade. */
val db15Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 15,
        kind = Kind.TOOL,
        section = "Library classification",
        title = "Cutter's Expansive Classification",
        originalTitle = "Expansive Classification, part 1 (1891)",
        author = "Charles Ammi Cutter",
        year = 1891,
        summary = "A library classification whose notation grows by adding letters and digits, so a new subject can be inserted without renumbering the rest.",
        details = "Cutter published the first part, a classification for a small library, in 1891, and later parts followed in 1893. The notation was built to expand, an early design for a subject code that could grow, set beside the Dewey Decimal scheme.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.TOOL,
        section = "Number theory reference",
        title = "Hilbert's Zahlbericht on number fields",
        originalTitle = "Die Theorie der algebraischen Zahlkoerper, Jahresbericht der Deutschen Mathematiker-Vereinigung (1897)",
        author = "David Hilbert",
        year = 1897,
        summary = "A long report that gathered the theory of algebraic number fields into one account, which became a standard reference for the following decades.",
        details = "Hilbert wrote the report for the German Mathematical Society, and it appeared in the society's yearbook in 1897. Its influence on class field theory in the next decades is well known.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.TOOL,
        section = "Mathematical tables",
        title = "Peirce's Short Table of Integrals",
        originalTitle = "A Short Table of Integrals (1899)",
        author = "Benjamin Osgood Peirce",
        year = 1899,
        summary = "A table of standard integrals, arranged by form, so a reader can look up an integral and check a result against it.",
        details = "Peirce's table first appeared in 1899 and went through several editions. It is a reference of the same kind as the logarithm tables of its day, and it is a different work from the logic notation of Charles Sanders Peirce.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.TOOL,
        section = "Nomograms",
        title = "d'Ocagne's Nomographie",
        originalTitle = "Nomographie, les calculs usuels effectues au moyen des abaques (1899)",
        author = "Maurice d'Ocagne",
        year = 1899,
        summary = "A book on alignment charts, which read a result where a straight line crosses three scales, so a formula could be worked by ruler.",
        details = "D'Ocagne's book of 1899 set out the theory of nomograms, the charts that solve an equation by lining up three scales. Such charts were an analogue calculating tool of the period and stand beside the slide rule.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.TOOL,
        section = "Reference works",
        title = "Encyklopadie der mathematischen Wissenschaften",
        originalTitle = "Encyklopadie der mathematischen Wissenschaften mit Einschluss ihrer Anwendungen (1898)",
        author = "Walther von Dyck and others, editors",
        year = 1898,
        summary = "A multi-volume encyclopedia of mathematics and its applications, begun in 1898, in which specialists wrote the articles.",
        details = "The encyclopedia was published by Teubner in Leipzig from 1898, with Walther von Dyck among its editors. It is included as a reference work of the decade, next to the catalogues of the period.",
        rare = true,
    ),

    entry(
        databaseId = 15,
        kind = Kind.TOOL,
        section = "Biographical reference",
        title = "Dictionary of National Biography, sixty-three volumes",
        originalTitle = "Dictionary of National Biography (1885 to 1900)",
        author = "Leslie Stephen and Sidney Lee, editors",
        year = 1900,
        summary = "A multi-volume dictionary of British lives, completed in 1900, that became a model for national biographies.",
        details = "Work began in 1885 under Leslie Stephen and was completed under Sidney Lee in 1900. It is a reference work of the decade, included with the catalogues and indexes of the period.",
    ),
)
