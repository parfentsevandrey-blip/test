package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Programming languages of 1970 to 1980: message-passing Smalltalk, structured preprocessors, a teaching subset of Pascal, a query language and a notation for processes. */
val db8Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 8,
        kind = Kind.LANGUAGE,
        section = "Object-oriented",
        title = "Smalltalk-72",
        originalTitle = "Smalltalk, the 1972 version at Xerox PARC",
        author = "Alan Kay, Dan Ingalls and Adele Goldberg",
        year = 1972,
        summary = "Alan Kay's language at Xerox PARC, the first Smalltalk in working form, in which objects exchanged messages and each object decided how to answer them.",
        details = "The first working version began as a BASIC prototype, moved to a Data General Nova, and reached the Alto through Nova emulation. Kay's design work for it dates from about 1971. " +
            "Standard references give 1972 as the first appearance and flag the earlier development start as needing a citation.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.LANGUAGE,
        section = "Preprocessors",
        title = "Ratfor",
        originalTitle = "Rational Fortran (Ratfor)",
        author = "Brian Kernighan at Bell Labs",
        year = 1975,
        summary = "A preprocessor that gave Fortran 66 structured control flow, with if, else, while and for, in place of jumps and statement labels.",
        details = "Kernighan built Ratfor at Bell Labs around 1974 and described it in 1975 in Software: Practice and Experience. Its control statements were borrowed from C. " +
            "The 1976 book Software Tools put Ratfor to work, and the first Unix manual page for it dates from Version 6.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.LANGUAGE,
        section = "Teaching languages",
        title = "Pascal-S",
        originalTitle = "Pascal-S, a teaching subset of Pascal",
        author = "Niklaus Wirth",
        year = 1975,
        summary = "A small teaching subset of Pascal, with a compact compiler, that Wirth designed so that students could read a complete implementation.",
        details = "Wirth developed Pascal-S in the mid-1970s, and its implementation was small enough for a course to study from start to finish. " +
            "The subset was the point of the design: a language whose compiler a student could understand.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.LANGUAGE,
        section = "Database languages",
        title = "QUEL",
        originalTitle = "QUEL, the query language of INGRES",
        author = "Michael Stonebraker, Eugene Wong and colleagues at Berkeley",
        year = 1976,
        summary = "The relational query language of the Berkeley INGRES system, which used range variables and its own syntax rather than SQL.",
        details = "INGRES was built at Berkeley from about 1973, and QUEL was its language for queries and updates. The design paper by Stonebraker, Wong, Kreps and Held dates from 1976, while the language was in use from 1974.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.LANGUAGE,
        section = "Concurrency",
        title = "Communicating Sequential Processes",
        originalTitle = "Communicating sequential processes (Hoare, 1978)",
        author = "Tony Hoare",
        year = 1978,
        summary = "Hoare's notation for processes that cooperate by sending messages to each other over named channels, with no shared variables.",
        details = "Hoare published the notation in 1978. It began as a notation rather than a programming language, and later languages such as occam took its ideas and made them into programs.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.LANGUAGE,
        section = "Microcomputer BASIC",
        title = "Altair BASIC",
        originalTitle = "Microsoft BASIC for the Altair 8800",
        author = "Bill Gates and Paul Allen",
        year = 1975,
        summary = "The BASIC interpreter that Microsoft wrote for the Altair 8800 in 1975, which gave hobbyists a language to run on a microcomputer with little memory.",
        details = "The interpreter fitted into about four kilobytes and became the base of many later microcomputer BASICs. The Altair was sold as a kit, and the interpreter was among the first programs that buyers loaded into it.",
    ),
)
