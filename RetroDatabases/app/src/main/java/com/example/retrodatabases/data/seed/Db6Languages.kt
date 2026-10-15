package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Programming languages of 1950 to 1960: the first autocodes, the first list and string languages and the ALGOL family. */
val db6Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 6,
        kind = Kind.LANGUAGE,
        section = "Early autocodes",
        title = "Glennie's Autocode",
        originalTitle = "Autocode for the Manchester Mark 1 (1952)",
        author = "Alick Glennie",
        year = 1952,
        summary = "Alick Glennie's symbolic system for the Manchester Mark 1, often called the first autocode, with mnemonic statements in place of raw machine code.",
        details = "Glennie built the system around 1952 for his own use on the Manchester Mark 1, whose machine code was hard to read. " +
            "Some historians call it an early compiler and others only a symbolic assembler, so the label depends on the definition. " +
            "The lecture notes that describe it are dated December 1952, and outside users never received it.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.LANGUAGE,
        section = "Interpreters",
        title = "Speedcoding",
        originalTitle = "Speedcoding interpretive system for the IBM 701",
        author = "John Backus and the IBM 701 team",
        year = 1953,
        summary = "John Backus's interpreter for floating-point arithmetic on the IBM 701. Programs ran ten to twenty times slower than machine code, but they were much quicker to write.",
        details = "Speedcoding was an interpreter: a program that stayed in the 701's memory carried out each instruction in turn. " +
            "The interpreter took about 310 words, which the usual account puts at roughly thirty per cent of the machine's storage. " +
            "Backus described it in a 1954 article in the Journal of the ACM, and it is generally called the earliest high-level language written for IBM hardware.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.LANGUAGE,
        section = "Early autocodes",
        title = "Mark 1 Autocode",
        originalTitle = "Mark 1 Autocode for the Ferranti Mark 1",
        author = "Brooker and the Manchester group",
        year = 1954,
        summary = "The autocode that other users at Manchester could actually run: a compiler for the Ferranti Mark 1 that translated named instructions into machine code.",
        details = "It came after Glennie's system and was the first autocode for the Ferranti Mark 1 that users outside the team could use. " +
            "Sources differ on the dates. One gives March 1954 for its release, and another gives 1954 for the planning and 1955 for the development work. The record uses 1954.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.LANGUAGE,
        section = "Business languages",
        title = "FLOW-MATIC",
        originalTitle = "FLOW-MATIC, first called B-0",
        author = "Grace Hopper and her team at Remington Rand",
        year = 1955,
        summary = "The English-like data processing language for the UNIVAC I, in which operations were written as sentences and data layouts were filled in on printed forms.",
        details = "The team first called the language B-0. Standard references give 1955 as the year it first appeared, while the B-0 compiler is dated to 1956 and the design to 1957 in other sources. " +
            "Several of its features, including the English-like statements, went into COBOL.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.LANGUAGE,
        section = "List processing",
        title = "IPL",
        originalTitle = "Information Processing Language (IPL-II and IPL-V)",
        author = "Allen Newell, Cliff Shaw and Herbert Simon",
        year = 1956,
        summary = "The first list-processing language, built for the Logic Theorist and the General Problem Solver, with programs and data held as linked lists.",
        details = "IPL first ran on the JOHNNIAC computer at RAND, and its machine-generated proofs began in late 1956 and early 1957. " +
            "Simon's autobiography tells how the first version was simulated by hand, with his children acting as the machine. " +
            "Lisp, which was influenced by IPL, soon replaced it for most work.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.LANGUAGE,
        section = "Early compilers",
        title = "IT, the Internal Translator",
        originalTitle = "Internal Translator (IT)",
        author = "Perlis, Smith and Van Zoeren",
        year = 1957,
        summary = "A compiler for mathematical problems on the IBM 650, begun at Purdue and finished at Carnegie Tech, and the ancestor of a family of later translators.",
        details = "The work began at Purdue around 1955 on a Datatron 205, and moved to the IBM 650 at Carnegie Tech about 1957. " +
            "Perlis's own essay places its completion shortly after Thanksgiving 1956, while the dictionaries give 1957. " +
            "The third version added double-precision floating-point arithmetic. Its successors include RUNCIBLE, GATE and CORRELATE, and Donald Knuth reportedly called it the first really useful compiler.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.LANGUAGE,
        section = "String processing",
        title = "COMIT",
        originalTitle = "COMIT, a string-processing language",
        author = "Victor Yngve and his group",
        year = 1957,
        summary = "The first string-processing language, made for machine translation, in which programs rewrote strings of symbols by rules. SNOBOL grew out of it.",
        details = "Work on COMIT ran from 1957 to 1965 on IBM 700- and 7000-series machines. " +
            "Its rules worked on text rather than on numbers, which suited the linguistics of machine translation. " +
            "The date of Yngve's mechanical-translation paper differs between sources, from 1957 to 1958, but the language itself is dated to 1957 almost everywhere.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.LANGUAGE,
        section = "The ALGOL family",
        title = "ALGOL 58",
        originalTitle = "International Algebraic Language (IAL), later ALGOL 58",
        author = "A joint committee of GAMM and the ACM, with John Backus and Alan Perlis",
        year = 1958,
        summary = "The first report on an international algebraic language, drafted at a meeting in Zurich. It became the base of ALGOL 60 and of JOVIAL.",
        details = "The committee met at ETH Zurich from late May into early June 1958. The name IAL was replaced by ALGOL 58, though the change took about a year to become official. " +
            "Alan Perlis reportedly called the name IAL 'unspeakable'. ALGOL 60 grew out of this report.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.LANGUAGE,
        section = "Military and real-time",
        title = "JOVIAL",
        originalTitle = "Jules' Own Version of IAL",
        author = "Jules Schwartz and the System Development Corporation",
        year = 1959,
        summary = "A language based on ALGOL 58, developed from 1959 for military and real-time programs by Jules Schwartz and his group.",
        details = "The work began in 1959 at the System Development Corporation. The name is usually read as Jules' Own Version of IAL, though a personal history page says the team first read it as 'Just Our Version of IAL'. " +
            "JOVIAL was used for many military programs over several decades, and its extensions aimed at large real-time systems.",
        rare = true,
    ),
)
