package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1920 to 1930: a picture cable, a census tabulator, an axiom system, a radio treaty and an army cipher service. */
val db12Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 12,
        kind = Kind.SYSTEM,
        section = "Picture transmission",
        title = "Bartlane cable picture system",
        originalTitle = "Bartlane process for sending newspaper pictures by cable (1920)",
        author = "Harry G. Bartholomew and Maynard D. McFarlane",
        year = 1920,
        summary = "A system that sent newspaper photographs by undersea cable between London and New York, coded as gray levels punched on telegraph tape.",
        details = "The process was devised in 1920 for the Daily Mirror. The sending side turned picture signals into digital codes and punched them on standard perforated telegraph tape, which an ordinary sender ran through the cable. The receiver made a copy of the tape and rebuilt the picture offline. Early versions used five gray levels. Wikipedia calls it the first digital imaging system.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.SYSTEM,
        section = "Census tabulation",
        title = "Census Bureau tabulating system",
        originalTitle = "The Bureau's own punched-card tabulator, first used in the 1920 count",
        author = "United States Census Bureau",
        year = 1920,
        summary = "The Census Bureau's own punched-card tabulators, first used in the 1920 count, read 24-column cards with wire brushes.",
        details = "The bureau built its own tabulators after leasing Hollerith machines became too costly. Each card was read by wire brushes that set off 50 relays and 60 electromagnetic counters. The same machines were reused, with changes, for the 1930 count. The cards were punched by hand with a stylus that was aimed at each hole, a labour-intensive and error-prone job.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.SYSTEM,
        section = "Set theory",
        title = "Zermelo-Fraenkel set theory",
        originalTitle = "Zermelo's axioms with replacement added (Fraenkel and Skolem, 1922)",
        author = "Adolf Fraenkel and Thoralf Skolem",
        year = 1922,
        summary = "Zermelo's axioms of set theory with the replacement axiom added, the form that became the default foundation for mathematics.",
        details = "Fraenkel and Skolem, working separately in 1922, found that Zermelo's axioms could not secure a set built from iterated power sets of the natural numbers, and both saw that replacement was needed. Skolem read the vague notion of a definite property as any property expressible in first-order logic. Von Neumann stated the regularity axiom explicitly in 1925, and the modern system adds it to Zermelo's.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.SYSTEM,
        section = "Radio regulation",
        title = "International Radiotelegraph Convention",
        originalTitle = "International Radiotelegraph Convention (Washington, 1927)",
        author = "Delegates of 78 governments",
        year = 1927,
        summary = "The treaty that governed radio communication between nations after the Washington conference of 1927.",
        details = "The talks ran from 4 October to 25 November 1927. Delegates of 78 governments signed the convention and its general regulations on the closing day, and 75 signed the supplementary rules. The American envoys left the supplementary regulations unsigned, and the Senate was asked to approve only the convention and the general regulations.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.SYSTEM,
        section = "Cryptanalysis",
        title = "Signals Intelligence Service",
        originalTitle = "Army cryptanalysis moved to the Signal Corps (1929)",
        author = "United States Army Signal Corps",
        year = 1929,
        summary = "The Army codebreaking service that took over cryptanalysis from the closed Black Chamber in 1929, under William Friedman.",
        details = "Army cryptography moved under the Signal Corps in May 1929, and William Friedman became chief about two months later. Secretary of State Henry Stimson had closed the Black Chamber, run by Herbert Yardley, that same year. Motives for the closure are disputed: budget, and Stimson's moral objection as Yardley reported it. Yardley's 1931 memoir, The American Black Chamber, led several nations to change their codes.",
    ),
)
