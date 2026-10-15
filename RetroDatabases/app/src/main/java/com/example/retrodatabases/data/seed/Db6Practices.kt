package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1950 to 1960: how programmers reused, debugged, scheduled and maintained their programs before compilers did most of the work. */
val db6Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 6,
        kind = Kind.PRACTICE,
        section = "Reuse",
        title = "Subroutine libraries",
        originalTitle = "The Preparation of Programs for an Electronic Digital Computer (1951)",
        author = "Maurice Wilkes, David Wheeler and Stanley Gill",
        year = 1951,
        summary = "A library of tested subroutines that a programmer copied into a new program, described in the first book on programming.",
        details = "The EDSAC at Cambridge kept such a library on tape, and users had its tapes copied by machine onto their own program tapes before the originals went back to storage. " +
            "The 1951 book by Wilkes, Wheeler and Gill explains how to prepare programs with these routines. A 1950 report by the same three authors came first, in only a few copies.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.PRACTICE,
        section = "Programming technique",
        title = "Index registers",
        originalTitle = "Index registers and address modification",
        author = "IBM 704 programmers",
        year = 1954,
        summary = "A register whose value is added to an address while the program runs, so one loop can walk through a table without changing its instructions.",
        details = "Before index registers, programmers changed the instructions in memory to step through an array, which was slow and easy to get wrong. " +
            "The IBM 704 had three index registers, and a tag field in each instruction chose which one to use. The Manchester machine had its B-lines, an earlier form of the same idea.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.PRACTICE,
        section = "Workflow",
        title = "Coding sheets and keypunching",
        originalTitle = "Coding forms and the card punch",
        author = "Programmers of the early 1950s",
        year = 1953,
        summary = "Programmers wrote their code by hand on printed coding forms, one instruction per line, and keypunch operators turned the sheets into cards.",
        details = "A coding form had columns for the label, the operation and the address. A mistake found after punching meant new cards, so the sheets were checked carefully before they went to the keypunch. " +
            "The practice lasted until assemblers and compilers took over most of the writing.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.PRACTICE,
        section = "Debugging",
        title = "Octal core dumps",
        originalTitle = "Core dumps and octal listings",
        author = "Programmers of the 1950s",
        year = 1955,
        summary = "Debugging by reading a printout of memory in octal, word by word, because interactive debugging did not yet exist.",
        details = "When a program failed, the programmer asked for a core dump, a printout of the memory, and worked through the octal values with the listing beside it. " +
            "Breakpoints and tracing came later. Some consoles had lamps that showed the registers, and the operator read them while the program stopped.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.PRACTICE,
        section = "Operations",
        title = "Batch processing with card decks",
        originalTitle = "Batch processing and job control cards",
        author = "The computer centres of the 1950s",
        year = 1955,
        summary = "Programmers handed in decks of cards and collected the printout later, so the computer ran jobs one after another from a queue.",
        details = "Control cards between a program and its data told the monitor what to do with each job. The turnaround between handing in a deck and getting the results back could take hours or days, which made every mistake expensive.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.PRACTICE,
        section = "Community",
        title = "SHARE user group",
        originalTitle = "SHARE, the IBM 704 users' group",
        author = "SHARE members",
        year = 1955,
        summary = "The user group of IBM 704 users that exchanged programs and knowledge, often called the earliest organised user group in computing.",
        details = "Delegates from the 704 installations met at RAND in August 1955, and the group's first big product was a reference manual for the 704 in 1956. " +
            "SHARE later wrote the SHARE Operating System for the 709, and its software library is counted among the roots of open-source software.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.PRACTICE,
        section = "Maintenance",
        title = "Patching object decks",
        originalTitle = "Patch cards and hand patches",
        author = "Programmers of the 1950s",
        year = 1956,
        summary = "Fixing a program by punching a few replacement cards, or by changing single words in memory, instead of punching the whole deck again.",
        details = "A patch card carried the address and the new value of one word. Patching was quicker than a new deck, but a patched deck was hard to follow, so a clean copy was punched when the work was done.",
        rare = true,
    ),
)
