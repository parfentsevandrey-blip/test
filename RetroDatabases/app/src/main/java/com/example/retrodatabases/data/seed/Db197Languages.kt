package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 70 to 80: Pliny the Elder's Natural History and Josephus's Jewish War, one Latin and one Greek book of the Flavian age. */
val db197Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 197,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Pliny the Elder's Natural History in thirty-seven books",
        originalTitle = "Naturalis Historia",
        author = "Pliny the Elder",
        year = 77,
        summary = "Pliny the Elder finished his Natural History, a Latin encyclopedia in thirty-seven books, in 77 and dedicated it to the emperor Titus.",
        details = "Its books 33 to 37 cover gold and silver, copper and iron, pigments, building stones and gems, which makes the work a source for the materials of tools and building. Pliny died in 79 near Stabiae during the eruption of Vesuvius, as Pliny the Younger reports in a letter, so the work was finished two years before its author died.",
    ),

    entry(
        databaseId = 197,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Josephus's Jewish War, the revolt against Rome told in seven books",
        originalTitle = "Peri tou Ioudaikou polemou",
        author = "Flavius Josephus",
        year = 75,
        summary = "Josephus's Jewish War tells the revolt of the Jews against Rome in seven books, and its Greek text is usually dated to about 75.",
        details = "Josephus first drafted the account in his ancestral tongue, which accounts give as Aramaic or Hebrew, and the surviving Greek version followed, with completion dated from 75 to 79. The book covers the siege of Jerusalem by Titus in 70 and the fall of Masada, and it is the main narrative source for the revolt.",
    ),

)
