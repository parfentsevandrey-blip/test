package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1950 to 1960: the compilers and assemblers that made the first large programs practical. */
val db6Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 6,
        kind = Kind.TOOL,
        section = "Compilers and linkers",
        title = "A-0 system",
        originalTitle = "A-0 for the UNIVAC I",
        author = "Grace Hopper and her team at Remington Rand",
        year = 1952,
        summary = "Grace Hopper's A-0 system for the UNIVAC I, which built a program by copying tested subroutines from a library. It is often called the first compiler.",
        details = "The work on A-0 ran in 1951 and 1952. Instead of translating a language, it assembled a program from the library routines a programmer called, so whether it counts as a compiler depends on the definition. " +
            "Libraries of tested routines were already in use on EDSAC.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.TOOL,
        section = "Assemblers",
        title = "SOAP",
        originalTitle = "Symbolic Optimal Assembly Program (SOAP)",
        author = "Stan Poley and IBM; sources differ on the authors",
        year = 1955,
        summary = "The optimising assembler for the IBM 650. It placed each instruction on the drum where the machine would reach it just in time.",
        details = "The 'Optimal' in the name refers to the drum. The assembler put each instruction in the slot the drum would bring under the head right after the previous instruction had finished. " +
            "Most references name Stan Poley as the developer, but some bibliographies credit other people for the 1955 IBM bulletin. " +
            "Donald Knuth's variants at Case Institute followed, SOAP III in 1958 and SuperSOAP in 1959. SOAP III was renamed CASE SOAP III after IBM objected to the name.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.TOOL,
        section = "Assemblers",
        title = "SAP for the IBM 704",
        originalTitle = "Symbolic Assembly Program (SAP), first called the Share Assembly Program",
        author = "Roy Nutt and the SHARE group",
        year = 1956,
        summary = "The two-pass assembler for the IBM 704, which SHARE began to circulate in 1956 and which became the default assembler for 704 users.",
        details = "Credit for the program goes to Roy Nutt of United Aircraft Corporation, and a 704 with at least 4K words of 36-bit core memory was needed to run it. " +
            "Sources differ on the dates: one gives 1953 for the design, while the 1956 date is for SHARE's distribution. Standard references say its notation became the template for later assembly languages.",
        rare = true,
    ),
)
