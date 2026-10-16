package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1380 to 1390: a Hanseatic cog, the clocks of Salisbury, Rouen and Dijon, and a disputed gun of Ghent. */
val db66Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 66,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The Salisbury Cathedral clock, about 1386",
        originalTitle = "Salisbury Cathedral clock",
        author = "The makers of the cathedral clock",
        year = 1386,
        summary = "The Salisbury Cathedral clock is a mechanical clock of about 1386, often named the oldest working clock in the world.",
        details = "The date is an estimate, and some historians doubt that the surviving mechanism is the one first installed, since it has been repaired and altered. The clock is driven by weights and regulated by a verge and foliot.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.DEVICE,
        section = "Guns",
        title = "Dulle Griet, the great iron gun of Ghent",
        originalTitle = "Dulle Griet",
        author = "The gunmakers of Ghent",
        year = 1382,
        summary = "Dulle Griet is a large gun of forged iron kept in Ghent, and one account dates it to 1382, although most accounts place it in the fifteenth century.",
        details = "Its name comes from a Flemish legend about a warrior woman, and the gun is made of iron bars held by iron rings rather than cast. The year 1382 is shown only because one account gives it, and the dispute over the date is part of the record.",
        rare = true,
    ),

    entry(
        databaseId = 66,
        kind = Kind.DEVICE,
        section = "Ships",
        title = "The Bremen cog, a Hanseatic ship built about 1380",
        originalTitle = "Bremer Hanse-Kogge",
        author = "The shipwrights of Bremen",
        year = 1380,
        summary = "The Bremen cog is a Hanseatic sailing ship built about 1380, found in the mud of the Weser in 1962.",
        details = "Tree-ring dating of its oak places the building of the ship about 1380. The cog is a flat-bottomed cargo ship of the kind used in the Hanseatic trade.",
        rare = true,
    ),

    entry(
        databaseId = 66,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The Gros-Horloge of Rouen, a clock of about 1389",
        originalTitle = "Gros-Horloge",
        author = "The makers of the Rouen clock",
        year = 1389,
        summary = "The Gros-Horloge of Rouen is a public clock in a tower over a street, whose mechanism is dated to about 1389.",
        details = "The tower and clock were altered in later centuries, so the date refers to the original mechanism. The clock is one of the early public clocks of France, and the record uses 1389 as the date usually given.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The Jacquemart of Dijon, a bell-striking figure set up in 1383",
        originalTitle = "Jacquemart de Notre-Dame de Dijon",
        author = "Philip the Bold, duke of Burgundy",
        year = 1383,
        summary = "The Jacquemart of Dijon is a metal figure that strikes the hours on a bell at Notre-Dame, and it was set up in 1383 by Philip the Bold.",
        details = "The figure is said to have come from Kortrijk after Philip's forces sacked that town, and accounts differ on whether the sack was in 1382 or 1383. Later figures were added to the set over the following centuries.",
    ),

)
