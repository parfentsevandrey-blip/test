package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Machines of 1940 to 1950: the relay calculators, the first electronic computers and the storage tube that gave the Baby its memory. */
val db10Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 10,
        kind = Kind.DEVICE,
        section = "Electronic computers",
        title = "ENIAC",
        originalTitle = "Electronic Numerical Integrator and Computer",
        author = "J. Presper Eckert and John Mauchly",
        year = 1945,
        summary = "ENIAC was an electronic computer built at the University of Pennsylvania from thousands of vacuum tubes, and it was programmed by cables and switches.",
        details = "It was completed in 1945 and publicly unveiled in February 1946. Its first large jobs were ballistic calculations for the United States Army. It was rewired in 1948 to run instruction sequences held in function tables.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.DEVICE,
        section = "Relay computers",
        title = "Harvard Mark I",
        originalTitle = "Automatic Sequence Controlled Calculator",
        author = "Howard Aiken and IBM",
        year = 1944,
        summary = "The Harvard Mark I was a large electromechanical calculator that ran its program in sequence from punched paper tape.",
        details = "IBM built the machine for Harvard, and it was working in 1944. It used electromechanical counters and relays, so it was slow beside the electronic machines that followed, but it could run long calculations without an operator at each step.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.DEVICE,
        section = "Codebreaking",
        title = "Colossus",
        originalTitle = "Colossus Mark 1 and Mark 2, electronic codebreaking machines",
        author = "Tommy Flowers and the team at Dollis Hill",
        year = 1943,
        summary = "Colossus was an electronic machine that helped break German teleprinter messages encrypted with the Lorenz cipher at Bletchley Park.",
        details = "The first Colossus was working in December 1943 at Dollis Hill, reached Bletchley Park in January 1944 and began operating there in February 1944. Max Newman's Newmanry set the problems it was built to solve. The Mark 2 was in service by 1 June 1944, and the machines stayed secret until the 1970s.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.DEVICE,
        section = "German relay computers",
        title = "Zuse Z3",
        originalTitle = "Z3, a relay computer with binary floating-point numbers",
        author = "Konrad Zuse",
        year = 1941,
        summary = "The Z3 was a relay computer that worked in binary floating-point arithmetic and read its program from punched film.",
        details = "Zuse completed the Z3 in 1941. It used normalised binary floating-point numbers, and its program was read from punched film rather than held in a memory. The machine was destroyed in an air raid during the war.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.DEVICE,
        section = "German relay computers",
        title = "Zuse Z4",
        originalTitle = "Z4, Zuse's largest relay computer",
        author = "Konrad Zuse",
        year = 1945,
        summary = "The Z4 was Zuse's most advanced relay computer, finished in 1945, and it survived the war to run again after it was moved to Switzerland.",
        details = "Zuse completed the Z4 in 1945 after the Z3 and the earlier models. The machine survived the war, was later moved to Switzerland, and ran there in the early 1950s. Its program was read from punched tape.",
        rare = true,
    ),

    entry(
        databaseId = 10,
        kind = Kind.DEVICE,
        section = "Stored program",
        title = "Manchester Baby",
        originalTitle = "Manchester Small-Scale Experimental Machine (SSEM)",
        author = "Frederic Williams and Tom Kilburn",
        year = 1948,
        summary = "The Baby ran a program held in its own electronic memory on 21 June 1948, and it is widely described as one of the first stored-program computers to run a program.",
        details = "Its memory was a Williams tube holding 1024 bits. The first program, written by Tom Kilburn, found the largest proper divisor of 2 to the 18th, which is 131072, in about 52 minutes. A simulation of that program appears as a separate example.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.DEVICE,
        section = "Relay and tube hybrid",
        title = "IBM SSEC",
        originalTitle = "Selective Sequence Electronic Calculator (1948)",
        author = "IBM",
        year = 1948,
        summary = "The SSEC was IBM's 1948 calculator, which mixed relays with vacuum tubes and was installed in a public showroom in New York.",
        details = "Its instructions were read from punched tape, and the machine was set up for visitors to watch. It was one of the first large electronic calculators to be shown to the public.",
        rare = true,
    ),

    entry(
        databaseId = 10,
        kind = Kind.DEVICE,
        section = "Memory",
        title = "Williams-Kilburn tube",
        originalTitle = "Williams tube, a cathode-ray storage tube (1946)",
        author = "Frederic Williams and Tom Kilburn",
        year = 1946,
        summary = "A cathode-ray tube that held each bit as a charged spot on its screen, and that served as the main memory of early machines such as the Baby.",
        details = "Research began at the Telecommunications Research Establishment in June 1946, and a one-bit store was working by November 1946. The Manchester Baby used one such tube for its memory, and the design was later used in other first-generation machines. An IEEE milestone prefers the name Williams-Kilburn, and some texts still say Williams tube.",
        rare = true,
    ),
)
