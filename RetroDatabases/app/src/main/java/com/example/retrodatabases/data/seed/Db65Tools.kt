package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1390 to 1400: Chaucer's instructions for an astrolabe, an English encyclopedia and a French handbook of verse. */
val db65Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 65,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Chaucer's Treatise on the Astrolabe, written for his son Lewis",
        originalTitle = "Treatise on the Astrolabe",
        author = "Geoffrey Chaucer",
        year = 1391,
        summary = "Chaucer wrote his Treatise on the Astrolabe in 1391 as a set of plain English instructions for his young son Lewis.",
        details = "The prologue names Lewis, who was then about ten years old, and the work draws on a Latin treatise ascribed to Messahalla. Most accounts date the treatise to 1391, though the text was copied and revised afterwards.",
    ),

    entry(
        databaseId = 65,
        kind = Kind.TOOL,
        section = "Encyclopedias",
        title = "Trevisa's English version of Bartholomaeus Anglicus, finished in 1398",
        originalTitle = "De proprietatibus rerum",
        author = "John Trevisa",
        year = 1398,
        summary = "John Trevisa finished his English translation of the encyclopedia De proprietatibus rerum in 1398, a long reference book on the properties of things.",
        details = "The encyclopedia was written in Latin by Bartholomaeus Anglicus in the 1240s, and Trevisa's version was made for Thomas, Lord Berkeley. Accounts differ on the exact year of completion, so 1398 is the usual date.",
        rare = true,
    ),

    entry(
        databaseId = 65,
        kind = Kind.TOOL,
        section = "Verse",
        title = "Deschamps's L'Art de dictier, a French handbook of verse",
        originalTitle = "L'Art de dictier",
        author = "Eustache Deschamps",
        year = 1392,
        summary = "Eustache Deschamps wrote L'Art de dictier in 1392, a French handbook that sets out the rules of verse and its fixed forms, among them the ballade, the rondeau and the virelai.",
        details = "Deschamps was a poet and royal official at the French court, and his treatise is one of the first French guides to writing verse by rules. The year 1392 is the usual date, and the treatise survives in several later copies.",
    ),

)
