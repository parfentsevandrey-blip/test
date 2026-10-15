package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1740 to 1750: a controlled trial at sea, sugar from beet, silver on copper, cast steel and a chain of monks. */
val db30Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 30,
        kind = Kind.PRACTICE,
        section = "Medicine",
        title = "Lind's controlled trial for scurvy",
        originalTitle = "A Treatise of the Scurvy",
        author = "James Lind",
        year = 1747,
        summary = "Sailors with scurvy were given different diets in pairs, so that the effect of each diet could be compared on the same ship.",
        details = "Lind carried out the trial aboard HMS Salisbury in 1747 with twelve men in six pairs. He published his account in 1753, and the pair given oranges and lemons recovered fastest.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.PRACTICE,
        section = "Chemistry",
        title = "Marggraf's sugar from the white beet",
        originalTitle = "Berlin Academy memoir on sugar in the white beet",
        author = "Andreas Sigismund Marggraf",
        year = 1747,
        summary = "Sugar was drawn from the white beet with water and alcohol, which showed that beet holds the same sugar as cane.",
        details = "Marggraf reported the extraction to the Berlin Academy in 1747. His method did not yet make beet sugar cheap, but it began the search for a beet crop that later became a sugar industry.",
        rare = true,
    ),

    entry(
        databaseId = 30,
        kind = Kind.PRACTICE,
        section = "Electricity",
        title = "Nollet's chain of monks for the speed of electricity",
        originalTitle = "Experiment with a chain of monks",
        author = "Jean-Antoine Nollet",
        year = 1746,
        summary = "A line of men joined by wire, through which an electric shock was passed, so that every man in the line felt it at the same moment.",
        details = "Nollet made the experiment in 1746 with a large group of monks at a Paris monastery, and he reported that the shock was felt by all of them at once. Accounts differ on how many monks took part and on how long the shock took to pass.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.PRACTICE,
        section = "Metalwork",
        title = "Sheffield plate, silver fused onto copper",
        originalTitle = "Sheffield plate",
        author = "Thomas Boulsover",
        year = 1743,
        summary = "Silver was fused onto a copper ingot, so that the two metals could be rolled into sheets and shaped as one piece.",
        details = "Boulsover is credited with the method in Sheffield, and accounts differ on the year of the discovery, which is given as 1742 in some histories and 1743 in others. Plated goods were cheaper than solid silver, which made the method popular.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.PRACTICE,
        section = "Metalwork",
        title = "Huntsman's crucible steel",
        originalTitle = "Crucible steel",
        author = "Benjamin Huntsman",
        year = 1740,
        summary = "Blister steel was melted in closed clay crucibles, which gave a uniform steel that could be cast and forged.",
        details = "Huntsman worked out the method in Sheffield around 1740, so that steel could be made in quantity with fewer impurities. Accounts differ on the year, which is given as 1740 in some histories and as the early 1740s in others.",
    ),
)
