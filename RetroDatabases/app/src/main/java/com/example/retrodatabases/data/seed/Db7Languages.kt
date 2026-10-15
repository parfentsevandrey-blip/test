package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Programming languages of 1960 to 1970: simulation, string and conversational languages, and the designs that shaped the decade. */
val db7Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 7,
        kind = Kind.LANGUAGE,
        section = "Simulation",
        title = "GPSS",
        originalTitle = "General Purpose Simulation System (GPSS)",
        author = "Geoffrey Gordon at IBM",
        year = 1961,
        summary = "A simulation language in which a model is written as transactions moving through blocks, while the language keeps the clock and the queues.",
        details = "GPSS was built at IBM in 1961 for modelling systems such as queues, factories and computer installations. " +
            "The simulator advances time and records the waiting, so the person writing the model only has to describe the flow.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.LANGUAGE,
        section = "String processing",
        title = "SNOBOL",
        originalTitle = "String Oriented Symbolic Language (SNOBOL)",
        author = "Farber, Griswold and Polonsky at Bell Labs",
        year = 1962,
        summary = "A string-processing language in which a statement matches a pattern against a string and replaces the match, which made text work short to write.",
        details = "SNOBOL went through several versions between 1962 and 1967, when SNOBOL4 was finished on the IBM 360. SNOBOL4 treated patterns as values that could be stored and combined. " +
            "The language grew out of COMIT, and Ralph Griswold later designed Icon at the University of Arizona.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.LANGUAGE,
        section = "Conversational languages",
        title = "JOSS",
        originalTitle = "JOHNNIAC Open Shop System (JOSS)",
        author = "Cliff Shaw and the RAND Corporation",
        year = 1963,
        summary = "An interactive language at RAND in which people typed short commands at a console and saw each result at once, one of the first conversational systems.",
        details = "Cliff Shaw designed JOSS between about 1960 and 1963, and a beta version ran on the JOHNNIAC in May 1963. Some historians call it the first conversational programming system, though the label is contested. " +
            "Some reference works give 1966 for its first appearance, and the record uses 1963 for the first runs.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.LANGUAGE,
        section = "Cambridge and London",
        title = "CPL",
        originalTitle = "Combined Programming Language (CPL)",
        author = "Barron, Buxton, Hartley, Nixon and Strachey",
        year = 1963,
        summary = "A large language designed jointly at Cambridge and London, with the aim of combining the best of ALGOL 60 and FORTRAN. Its simpler descendant, BCPL, came later.",
        details = "CPL was never implemented in its full form. Its designers cut it down to BCPL, which was used for compilers and for the first B and C tools, so the family tree runs from CPL through BCPL to C.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.LANGUAGE,
        section = "The ALGOL family",
        title = "ALGOL W",
        originalTitle = "ALGOL W, a successor to ALGOL 60",
        author = "Niklaus Wirth and Tony Hoare",
        year = 1966,
        summary = "A smaller and cleaner successor to ALGOL 60, with records and pointers, implemented for the IBM System/360 at Stanford.",
        details = "Wirth and Hoare published the design in 1966. It added records and pointers to the ALGOL 60 model, and it influenced the languages that followed, Pascal among them.",
    ),

    entry(
        databaseId = 7,
        kind = Kind.LANGUAGE,
        section = "Medical computing",
        title = "MUMPS",
        originalTitle = "Massachusetts General Hospital Utility Multi-Programming System",
        author = "Neil Pappalardo, Robert Greenes and Curt Marble",
        year = 1966,
        summary = "A language and database for hospital records, with a hierarchical store built into the language itself.",
        details = "The first version was written in 1966 and 1967 at Massachusetts General Hospital, on a DEC PDP-7. Pappalardo helped start Meditech in 1969, and that company still uses a proprietary form of the language. " +
            "The language was standardised in 1976 and 1977, and sources differ on the exact years.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.LANGUAGE,
        section = "Language design",
        title = "ISWIM",
        originalTitle = "If you See What I Mean (ISWIM)",
        author = "Peter Landin",
        year = 1966,
        summary = "A language design rather than a finished system: Landin's proposal for the next 700 languages, built on expressions and functions, with an ALGOL-like notation.",
        details = "Landin's paper 'The Next 700 Programming Languages' appeared in 1966. ISWIM was a family of languages built on a common core of expressions, and its layout rule, the offside rule, later appeared in Haskell. " +
            "It was never implemented as a language people used day to day.",
        rare = true,
    ),
)
