package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1940 to 1950: the tapes, cards, keyboards and calculators that people used to prepare and run the programs of the decade. */
val db10Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 10,
        kind = Kind.TOOL,
        section = "Program input",
        title = "Paper tape on the Harvard Mark I",
        originalTitle = "Punched paper tape as the program medium (1944)",
        author = "Howard Aiken",
        year = 1944,
        summary = "The Mark I read its instructions from a punched paper tape, so a long calculation was a tape rather than a set of plugboard connections.",
        details = "Each tape carried the sequence of instructions that set the order of operations, and the machine's counters and relays carried out the steps. Paper tape was the medium that made the Mark I program-controlled.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.TOOL,
        section = "Punched cards",
        title = "IBM 602 calculating punch",
        originalTitle = "IBM 602 Calculating Punch (1946)",
        author = "IBM",
        year = 1946,
        summary = "The IBM 602 read a punched card, did arithmetic on its contents and punched the answer back into the card.",
        details = "It was controlled by a plugboard that defined the calculation, so one card machine could be set up for different jobs. It belonged to IBM's line of calculating punches, which led on to the larger card-programmed machines of the later 1940s.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.TOOL,
        section = "Keyboards and tape",
        title = "Friden Flexowriter",
        originalTitle = "Friden Flexowriter, a typewriter that punched paper tape",
        author = "Friden Calculating Machine Company",
        year = 1947,
        summary = "The Flexowriter was a powered typewriter that punched paper tape as it typed, and the tape could be read back to drive a machine.",
        details = "A University of Iowa history describes the machine as fully developed by 1947, and it was later used with early computers to prepare and read program tapes. Its roots go back to the 1920s, and Friden came to control its makers in the 1950s.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.TOOL,
        section = "Punched cards",
        title = "IBM 603 electronic multiplier",
        originalTitle = "IBM 603 Electronic Multiplier (1948)",
        author = "IBM",
        year = 1948,
        summary = "The IBM 603 was an electronic multiplier that worked with punched card machines, multiplying numbers at electronic speed under the direction of program cards.",
        details = "It was installed alongside an accounting machine in 1948 at Hawthorne in California, with punched program cards directing the sequence of operations. Accounts differ on who assembled the first unit, so only the year is given.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.TOOL,
        section = "Input and output",
        title = "Creed teleprinter on EDSAC",
        originalTitle = "Teleprinter input and output of the EDSAC (1949)",
        author = "Creed and Company",
        year = 1949,
        summary = "EDSAC read its programs from paper tape and printed its results on a teleprinter, so its operators read the output as typed text.",
        details = "The teleprinter and the tape reader were the machine's main input and output devices. The first EDSAC program was run in May 1949 with this equipment.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.TOOL,
        section = "Mechanical calculators",
        title = "Curta pocket calculator",
        originalTitle = "Curta mechanical calculator, designed by Curt Herzstark",
        author = "Curt Herzstark",
        year = 1948,
        summary = "The Curta was a hand-held mechanical calculator that could add, subtract, multiply and divide, which put a working calculator in a pocket.",
        details = "Herzstark designed it in Austria over many years, and production began in the late 1940s. It shows that the decade also brought mechanical calculation to individual desks, not only to large machines. The year 1948 is given for its introduction.",
    ),
)
