package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1900 to 1910: wireless and cable links, a radio tower, and the formal systems of set theory and logic. */
val db14Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 14,
        kind = Kind.SYSTEM,
        section = "Radio",
        title = "Marconi's transatlantic wireless",
        originalTitle = "Wireless telegraph signals from Poldhu, Cornwall, to St. John's, Newfoundland (1901)",
        author = "Guglielmo Marconi",
        year = 1901,
        summary = "Radio signals cross the Atlantic: in December 1901 a receiver in Newfoundland picked up the Morse letter S sent from Cornwall.",
        details = "The transmitter stood at Poldhu in Cornwall and the receiver at St. John's, Newfoundland. Marconi reported three dots, the letter S, on 12 December 1901. Regular commercial service across the Atlantic began only in 1907.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.SYSTEM,
        section = "Telegraph",
        title = "All Red Route, the Pacific telegraph cable",
        originalTitle = "Pacific Cable Board, the all-British cable route (1902)",
        author = "Pacific Cable Board",
        year = 1902,
        summary = "A submarine telegraph cable across the Pacific links Canada with Fanning Island, Fiji and Australia over British territory only.",
        details = "Wikipedia and the Queensland heritage register date the launch of the route to 31 October 1902. Another source dates the Bamfield station to 1904, so accounts differ on when the whole route was in service. The Pacific Cable Board was set up in 1901 to direct the project.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.SYSTEM,
        section = "Foundations",
        title = "Zermelo's axioms for sets",
        originalTitle = "Untersuchungen uber die Grundlagen der Mengenlehre I (1908)",
        author = "Ernst Zermelo",
        year = 1908,
        summary = "Zermelo gives the first full list of axioms for sets, and the separation axiom picks out members only from a set that already exists.",
        details = "The 1908 paper lists axioms for extensionality, pairing, union, power sets, choice, infinity and separation. Separation is what blocks the paradox of the set of all sets that do not contain themselves. Later axiom lists add replacement and foundation.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.SYSTEM,
        section = "Foundations",
        title = "Russell's theory of types",
        originalTitle = "Mathematical logic as based on the theory of types (1908)",
        author = "Bertrand Russell",
        year = 1908,
        summary = "Each object has a type, and a set may only contain objects of a lower type, so no set can be a member of itself.",
        details = "Russell published the theory in 1908 as the answer to the paradoxes that followed his 1901 discovery. The ramified version adds orders within each type, and Principia Mathematica used it.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.SYSTEM,
        section = "Radio",
        title = "The Eiffel Tower as a radio station",
        originalTitle = "Military wireless experiments on the Eiffel Tower, from 1903",
        author = "Gustave Ferrie",
        year = 1903,
        summary = "The Eiffel Tower in Paris was used for radio experiments and later for time signals, reaching thousands of kilometres by 1908.",
        details = "Eiffel's offer of the tower in 1903 let Ferrie carry out military wireless experiments there. The official tower history gives a range of about 6000 km by 1908 and regular time signals from May 1910. Accounts differ on who first proposed the use.",
    ),
)
