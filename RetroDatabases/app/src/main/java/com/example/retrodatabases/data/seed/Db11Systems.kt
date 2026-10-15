package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1930 to 1940: machines working together, and the networks and methods that carried their work. */
val db11Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 11,
        kind = Kind.SYSTEM,
        section = "Analog computing",
        title = "The differential analyser, integrators linked by shafts",
        originalTitle = "The differential analyzer, a new machine for solving differential equations (Bush, 1931)",
        author = "Vannevar Bush and colleagues at MIT",
        year = 1931,
        summary = "A set of mechanical integrators, linked by shafts and gears, solved a differential equation as the operator turned a crank.",
        details = "Bush and his group at MIT described the machine in 1931 in the Journal of the Franklin Institute. Its integrators were wheel-and-disc units, and an operator set up an equation by connecting the units with shafts. Other analysers were built in other countries later in the decade.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.SYSTEM,
        section = "Cryptanalysis",
        title = "The Polish method against the Enigma",
        originalTitle = "The Polish Cipher Bureau methods and machines for Enigma (1932 to 1938)",
        author = "Marian Rejewski and colleagues",
        year = 1932,
        summary = "Polish cryptanalysts worked out the wiring of the army Enigma from the permutations that repeated message keys produced, and then built machines to find the daily settings.",
        details = "Marian Rejewski solved the wiring in December 1932 by analysing the permutations that the doubled message keys made. The bomba machine of 1938 searched the rotor positions faster. The Polish results were given to the British and French in 1939, before the war began.",
        rare = true,
    ),

    entry(
        databaseId = 11,
        kind = Kind.SYSTEM,
        section = "Accounting",
        title = "Plugboard-wired accounting with the IBM 405",
        originalTitle = "IBM 405 alphabetic accounting machine (1934)",
        author = "International Business Machines",
        year = 1934,
        summary = "A punched-card system in which a removable plugboard set the layout of the printed reports, so one machine could serve many jobs.",
        details = "IBM introduced the 405 in 1934, and it stopped marketing the machine in 1949. It added, subtracted and printed letters as well as figures from punched cards, and the plugboard worked like a telephone switchboard to route the fields. It was a common way to run business accounts on punched cards before electronic computers.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.SYSTEM,
        section = "Communications",
        title = "The German telex network of 1933",
        originalTitle = "Telex, the teleprinter exchange service of the Reichspost (1933)",
        author = "Deutsche Reichspost",
        year = 1933,
        summary = "Subscribers connected their teleprinters through an exchange and exchanged typed messages, with the connection set up for each call.",
        details = "The German public service opened in 1933, with Berlin and Hamburg among the first cities connected, and it ran at 50 baud, roughly 66 words a minute. The United States had an earlier teletypewriter exchange, which AT&T opened on 21 November 1931. The five-bit telegraph code of the decade carried the messages.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.SYSTEM,
        section = "Remote access",
        title = "Teletype access to a calculator from a meeting in 1940",
        originalTitle = "Complex number calculator, demonstrated remotely at Dartmouth (Stibitz, 1940)",
        author = "George Stibitz",
        year = 1940,
        summary = "Commands typed on a teletype in Hanover, New Hampshire travelled over telephone lines to a relay calculator in New York, and the answers came back to the same terminal.",
        details = "The demonstration took place on 11 September 1940, at the joint meeting of the American Mathematical Society and the Mathematical Association of America at Dartmouth. The calculator, finished in 1939, used about 450 telephone relays and handled eight-digit multiplication and division. Historians describe the demonstration as the earliest or probably the earliest remote use of a computing machine.",
        rare = true,
    ),
)
