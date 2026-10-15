package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1970 to 1980: virtual machines, a timesharing system with paging, two early relational databases and a commercial machine with an integrated database. */
val db8Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 8,
        kind = Kind.SYSTEM,
        section = "Virtual machines",
        title = "VM/370",
        originalTitle = "Virtual Machine/370 with CMS",
        author = "IBM",
        year = 1972,
        summary = "IBM's virtual machine system, which gave each user a copy of the whole computer, with the Conversational Monitor System as the operating system that people used.",
        details = "IBM unveiled VM/370 on 2 August 1972. It grew out of CP-40 in 1964 and CP-67 in 1967, and its guest system began life as the Cambridge Monitor System. Descendants of the system are still in use.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.SYSTEM,
        section = "Timesharing",
        title = "TENEX",
        originalTitle = "TENEX operating system for the PDP-10",
        author = "Bolt Beranek and Newman (BBN)",
        year = 1972,
        summary = "The operating system that BBN built for the PDP-10, with paging and virtual memory, introduced in a Communications of the ACM paper in March 1972.",
        details = "BBN needed paging for its memory-hungry Lisp work. DEC had declined to add paging to the KA-10, so BBN designed its own pager. DEC's TOPS-20 came from a TENEX variant that BBN licensed. " +
            "Dates for TENEX itself vary from 1969 to 1972.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.SYSTEM,
        section = "Relational databases",
        title = "INGRES",
        originalTitle = "INGRES, the Interactive Graphics and Retrieval System",
        author = "Michael Stonebraker, Eugene Wong and their Berkeley group",
        year = 1973,
        summary = "The relational database built at Berkeley, which used the QUEL language and became one of the first working relational systems, alongside IBM's System R.",
        details = "The project began around 1973, when IBM's System R papers drew Stonebraker and Wong into relational research, and a working prototype existed by 1974. " +
            "Stonebraker led the work until 1985, and Postgres, his successor, became PostgreSQL.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.SYSTEM,
        section = "Relational databases",
        title = "System R",
        originalTitle = "IBM System R, a research relational database",
        author = "IBM San Jose Research; Donald Chamberlin and Raymond Boyce among others",
        year = 1974,
        summary = "IBM's research relational database at San Jose, often credited as the first implementation of SQL, and the source of query-optimisation ideas that later systems used.",
        details = "Work began in 1974. Phase Zero, in 1975, gave an early SQL interface. Phase One, in 1976 and 1977, built the complete multi-user system, and Phase Two, in 1978 and 1979, tested it with real users at San Jose and at outside sites. " +
            "Codd's relational model was its basis.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.SYSTEM,
        section = "Commercial systems",
        title = "IBM System/38",
        originalTitle = "IBM System/38",
        author = "IBM",
        year = 1978,
        summary = "IBM's System/38, announced on 24 October 1978, with an object-based design, 48-bit addressing and an integrated database. The AS/400 was later built on its architecture.",
        details = "The design grew out of IBM's Future Systems work of the late 1960s and early 1970s. Standard references give August 1979 for commercial availability, while a 1980 Datapro report lists first delivery as scheduled for July 1980.",
        rare = true,
    ),
)
