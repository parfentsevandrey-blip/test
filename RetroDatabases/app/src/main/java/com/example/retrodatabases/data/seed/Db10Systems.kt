package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1940 to 1950: the designs and arrangements around the machines, from the stored-program plan to the card-programmed calculators. */
val db10Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 10,
        kind = Kind.SYSTEM,
        section = "Machine design",
        title = "Automatic Computing Engine design",
        originalTitle = "Proposed Electronic Calculator, Turing's ACE report (1945)",
        author = "Alan Turing",
        year = 1945,
        summary = "Turing's 1945 report for the National Physical Laboratory described a stored-program electronic calculator, the Automatic Computing Engine.",
        details = "Turing took up his post at the National Physical Laboratory on 1 October 1945, and the report was completed by the end of that year. Sources give the presentation date as early 1946, so 1945 is the year given. Every ACE machine, including the Pilot ACE, rests on this design. The report was reprinted in 1972.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.SYSTEM,
        section = "Relay systems",
        title = "Bell Labs Model V relay computer",
        originalTitle = "Model V, a relay calculator at Bell Telephone Laboratories (1946)",
        author = "Bell Telephone Laboratories",
        year = 1946,
        summary = "The Model V was a large relay calculator built from telephone switching parts, so it ran far more slowly than the electronic machines that followed.",
        details = "It was completed in 1946 and was one of the largest relay machines of its time. Relays switched far more slowly than vacuum tubes, which is why the electronic designs that followed were faster.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.SYSTEM,
        section = "ENIAC",
        title = "ENIAC master programmer",
        originalTitle = "Master programmer and portable function tables, ENIAC (1948)",
        author = "ENIAC design team",
        year = 1948,
        summary = "In 1948 ENIAC was changed so that its instructions came from portable function tables, sequenced by a master programmer, instead of from cables and panel switches.",
        details = "Before April 1948 operators set each unit with panel switches and cables. The change let the machine run instruction sequences in order, with the function tables holding the instructions. Guinness dates the decision to early 1948, and a Drexel course dates the change to April 1948, so 1948 is the year given.",
        rare = true,
    ),

    entry(
        databaseId = 10,
        kind = Kind.SYSTEM,
        section = "Punched cards",
        title = "IBM 604 electronic calculating punch",
        originalTitle = "IBM 604 Electronic Calculating Punch and its card-programmed sequences (1948)",
        author = "IBM",
        year = 1948,
        summary = "The IBM 604 combined vacuum-tube arithmetic with a card punch, and a plugboard or punched card decided which operations ran.",
        details = "The 604 surfaced in 1948 and could run sequences of as many as 60 steps. A board on the 521 card unit chose which card columns were read and which were punched. The commercial card-programmed calculator arrived in 1949. Sources give different counts of the units built, so no count appears here.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.SYSTEM,
        section = "Communication",
        title = "Shannon's communication system model",
        originalTitle = "A Mathematical Theory of Communication (Shannon, 1948)",
        author = "Claude Shannon",
        year = 1948,
        summary = "Shannon split communication into a source, an encoder, a noisy channel, a decoder and a destination, so that each part could be studied and measured on its own.",
        details = "The model comes from Shannon's 1948 paper, which also introduced the entropy measure. The noise in the channel is what limits the rate at which information can be sent without error. The entropy measure from the same paper is the subject of a separate example.",
    ),
)
