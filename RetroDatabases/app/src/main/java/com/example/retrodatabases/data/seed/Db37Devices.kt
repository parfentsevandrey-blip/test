package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1670 to 1680: spring watches and escapements, a reflecting telescope, a sun-viewing instrument, a gunpowder engine and a digester. */
val db37Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 37,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Huygens's balance-spring watch",
        originalTitle = "Balance spring watch",
        author = "Christiaan Huygens",
        year = 1675,
        summary = "Huygens fitted a watch with a spiral balance spring in 1675, so the balance wheel kept a steadier beat than the older balances did.",
        details = "Huygens described the spring in 1675 and had watches made with it. Robert Hooke also claimed the idea, and accounts differ on which of the two first conceived it.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Clement's anchor escapement",
        originalTitle = "Anchor escapement",
        author = "William Clement",
        year = 1671,
        summary = "The anchor escapement, which releases a clock's train one tooth at a time, is credited in 1671 to William Clement, though Hooke's earlier claim is also argued.",
        details = "A tower clock built in 1671 for King's College, Cambridge, is the earliest firm evidence for Clement's design. Hooke's claim to the anchor dates from about 1657 and rests on a reference of 1696, and Knibb is named as a rival in some accounts.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Hooke's constant-force escapement",
        originalTitle = "Constant-force escapement",
        author = "Robert Hooke",
        year = 1671,
        summary = "Hooke described a constant-force escapement in 1671, a way of giving a clock's train a steady drive from its spring.",
        details = "The date of 1671 is given for this design in some accounts, and it is separate from the anchor escapement whose priority is disputed. The design aimed to keep the drive steady while the spring unwound.",
        rare = true,
    ),

    entry(
        databaseId = 37,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Cassegrain's reflecting telescope",
        originalTitle = "Journal des scavans",
        author = "Laurent Cassegrain",
        year = 1672,
        summary = "Cassegrain's reflecting telescope folds the light path with a convex secondary mirror in front of a concave primary, so the tube can be short.",
        details = "The design was printed in the Journal des scavans in 1672, and accounts differ on the issue date, given as 15 April, 25 April or 29 February. Laurent Cassegrain, a priest, is credited as the likely originator, though the priority of the layout is argued.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Richer's clock at Cayenne",
        originalTitle = "Clock taken to Cayenne",
        author = "Jean Richer",
        year = 1672,
        summary = "Jean Richer took a Paris clock to Cayenne in 1672 and found that it lost time there, a clue to the shape of the Earth.",
        details = "Richer's observations at Cayenne, begun in 1672, showed that a clock regulated in Paris ran slower near the equator. Huygens and Newton discussed the result as evidence about gravity and the shape of the Earth.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.DEVICE,
        section = "Engines",
        title = "Huygens's gunpowder engine",
        originalTitle = "Gunpowder engine",
        author = "Christiaan Huygens",
        year = 1673,
        summary = "Huygens designed an engine driven by a gunpowder explosion in a cylinder, a forerunner of the piston engines of the next century.",
        details = "Denis Papin worked with Huygens on the engine in Paris. This record uses 1673 for the first trials, and accounts differ with another source that dates the design and build to 1678 to 1679.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Hooke's helioscope for viewing the sun",
        originalTitle = "A Description of Helioscopes",
        author = "Robert Hooke",
        year = 1676,
        summary = "Hooke's Description of Helioscopes of 1676 set out instruments for observing the sun safely, with its image seen through dark glass or projected.",
        details = "Hooke printed the description in 1676, and the same book carried the anagram that stood for his law of springs. The instruments were meant for watching the sun's disk and its spots without harm to the eye.",
        rare = true,
    ),

    entry(
        databaseId = 37,
        kind = Kind.DEVICE,
        section = "Cooking and pressure",
        title = "Papin's steam digester",
        originalTitle = "Nouvelle methode pour amollir les os",
        author = "Denis Papin",
        year = 1679,
        summary = "Papin's digester is a sealed pot whose lid holds the steam, so water inside can be heated well above its normal boiling point.",
        details = "This record uses 1679, the year Papin is credited with the design, and the printed account followed in 1681 under the title Nouvelle methode pour amollir les os. The digester softened bone so that its nourishment and gelatine could be drawn out, and it is an early example of a pressure vessel.",
    ),

)
