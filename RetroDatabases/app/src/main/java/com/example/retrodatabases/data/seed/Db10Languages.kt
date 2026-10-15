package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages and notations of 1940 to 1950: the first ways of writing programs that were not machine code, and the first codes of the stored-program machines. */
val db10Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 10,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "Plankalkul",
        originalTitle = "Plankalkul, Zuse's programming notation (designed 1942 to 1945)",
        author = "Konrad Zuse",
        year = 1945,
        summary = "Konrad Zuse's Plankalkul was a programming notation with typed variables and a layout on several rows, and no machine of his time ran it.",
        details = "Zuse worked on the design from 1942 to 1945. A first public description appeared in 1948 and a fuller book in 1972, and the first working implementation came in the late 1990s. Sources give slightly different design years, so 1945 is the year given. Each formula was written on several rows, with separate rows for the variable, its index and its type, which set it apart from the notations in use at the time.",
        rare = true,
    ),

    entry(
        databaseId = 10,
        kind = Kind.LANGUAGE,
        section = "Early notations",
        title = "Short Code",
        originalTitle = "Brief Code, proposed by John Mauchly in 1949",
        author = "John Mauchly",
        year = 1949,
        summary = "Short Code let programmers write algebraic expressions that an interpreter carried out, an early step towards the high-level languages.",
        details = "Mauchly proposed the idea in late July 1949 under the name Brief Code. A version was made for the BINAC, and a UNIVAC I version followed in 1950 under the name Short Code. Programmers still converted each expression by hand into two-character codes packed into 12-byte words, and the interpreter was slow. Sources disagree on which machine first ran it, so 1949 is the year given.",
        rare = true,
    ),

    entry(
        databaseId = 10,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "Flow diagrams for planning a program",
        originalTitle = "Planning and Coding of Problems for an Electronic Computing Instrument (Goldstine and von Neumann, 1947)",
        author = "Herman Goldstine and John von Neumann",
        year = 1947,
        summary = "Goldstine and von Neumann drew a program as a labelled graph of boxes, one for each step, so the order of operations could be checked before any coding.",
        details = "The report was issued by the Institute for Advanced Study in parts dated 1947 and 1948, and it introduced the flow diagram as a way to plan programs. The label later became flowchart. The preface of the first part is dated 1 April 1947, though some lists give 1946. The originality of the idea is disputed, because flowcharts had earlier uses in industry.",
        rare = true,
    ),

    entry(
        databaseId = 10,
        kind = Kind.LANGUAGE,
        section = "ENIAC",
        title = "Programming ENIAC with cables and switches",
        originalTitle = "ENIAC's panel switches and interconnecting cables, before the 1948 change",
        author = "J. Presper Eckert and John Mauchly",
        year = 1945,
        summary = "Before 1948 a program for ENIAC was a wiring plan: panel switches set each unit's function and cables carried the pulses that sequenced the operations.",
        details = "Operators chose each unit's function with switch settings and linked the units with cables, so changing a program meant rewiring. ENIAC was completed in 1945. The move away from cables, to instruction sequences held in function tables, came in 1948.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.LANGUAGE,
        section = "EDSAC",
        title = "EDSAC order code and initial orders",
        originalTitle = "EDSAC order code and its initial orders (Wilkes, Wheeler and Gill, 1949)",
        author = "Maurice Wilkes, David Wheeler and Stanley Gill",
        year = 1949,
        summary = "EDSAC programs were written with a short letter code for each order, and a set of 31-word initial orders read those codes and turned them into machine words.",
        details = "The initial orders were the first program loaded into the machine, and they read orders from paper tape. EDSAC ran its first program on 6 May 1949. Wikipedia calls the initial orders the earliest assembler of their kind, a claim that is disputed, so this record does not use the word first.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.LANGUAGE,
        section = "EDVAC",
        title = "EDVAC instruction code",
        originalTitle = "First Draft of a Report on the EDVAC (1945)",
        author = "John von Neumann",
        year = 1945,
        summary = "The 1945 draft of the EDVAC report set out a stored-program machine whose instructions sat in the same memory as its data.",
        details = "The First Draft of a Report on the EDVAC, dated 30 June 1945, describes the stored-program design that later became known as the von Neumann architecture, together with the operations the machine would carry out. The draft was incomplete and circulated among the Moore School team, so it describes a design rather than working code.",
    ),
)
