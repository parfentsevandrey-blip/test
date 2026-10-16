package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1490 to 1500: a royal library in Buda, a Venetian press, a line drawn across the ocean, a banking network and an imperial court. */
val db55Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 55,
        kind = Kind.SYSTEM,
        section = "Printing houses",
        title = "The Aldine press in Venice",
        originalTitle = "Aldine press",
        author = "Aldus Manutius",
        year = 1494,
        summary = "Aldus Manutius set up his press at Venice in 1494 and planned it as a house for editions of Greek and Latin classics, printed in type cut for the purpose.",
        details = "The press began to print in 1494, and its first books are dated 1494 and 1495. Its Greek editions were set in types cut for it by Francesco Griffo, and the house worked with editors who prepared the texts before they went to the press.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.SYSTEM,
        section = "Treaties",
        title = "The Treaty of Tordesillas and its line across the ocean",
        originalTitle = "Tratado de Tordesillas",
        author = "Castile and Portugal",
        year = 1494,
        summary = "The Treaty of Tordesillas of 1494 drew a line 370 leagues west of the Cape Verde islands and gave the lands beyond it to Castile and those on the Portuguese side to Portugal.",
        details = "The treaty was agreed at Tordesillas on 7 June 1494, and it replaced the line of May 1493 that the papal bull Inter caetera had drawn 100 leagues west of the Azores and the Cape Verde islands. Accounts differ on where the line falls in modern terms, because the leagues were measured by pilots and not by longitude.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.SYSTEM,
        section = "Libraries",
        title = "The Corvinian Library of Buda",
        originalTitle = "Bibliotheca Corviniana",
        author = "King Matthias Corvinus of Hungary",
        year = 1490,
        summary = "The royal library of King Matthias Corvinus at Buda was one of the largest book collections in Europe, and it began to be scattered after his death in 1490.",
        details = "Matthias died in April 1490, and his library held Greek and Latin manuscripts, many copied and illuminated by scribes brought from Florence. Accounts differ on how many volumes it held, and the books were dispersed in the Ottoman wars that followed.",
        rare = true,
    ),

    entry(
        databaseId = 55,
        kind = Kind.SYSTEM,
        section = "Banking",
        title = "The Medici bank, a network of branches",
        originalTitle = "Banco dei Medici",
        author = "The Medici family",
        year = 1494,
        summary = "The Medici bank ran a network of branches in several European cities, and the family's expulsion from Florence in 1494 ended its control of the business.",
        details = "Piero de' Medici was driven out of Florence in November 1494, and the branches had been run by managers who kept their accounts in ledgers. Accounts differ on the causes of the bank's failure, and the record gives only the year of the expulsion.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.SYSTEM,
        section = "Courts",
        title = "The Imperial Chamber Court of 1495",
        originalTitle = "Reichskammergericht",
        author = "Diet of Worms under King Maximilian I",
        year = 1495,
        summary = "The Imperial Chamber Court was set up at the Diet of Worms in 1495 as the highest court for the German lands of the Holy Roman Empire, under the Ewiger Landfriede, the perpetual peace.",
        details = "The court was created at the Diet of Worms in the same year as the Ewiger Landfriede, which banned private feuds, and it heard cases brought from across the empire. Its proceedings were recorded in writing from its first sittings, and the records of its cases are among the sources for the period.",
        rare = true,
    ),

)
