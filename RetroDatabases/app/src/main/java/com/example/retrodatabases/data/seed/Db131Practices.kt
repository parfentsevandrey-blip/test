package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 730 to 740: Bede's dating of events by the years from the Incarnation in his history of 731. */
val db131Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 131,
        kind = Kind.PRACTICE,
        section = "Latin",
        title = "Bede's dating of events from the Incarnation in his Ecclesiastical History of 731",
        originalTitle = "anno ab incarnatione Domini",
        author = "Bede",
        year = 731,
        summary = "Bede dates the events of his Ecclesiastical History by the years from the Incarnation, a count that Dionysius Exiguus proposed in 525 in an Easter table.",
        details = "Bede's history gave the count a wide Latin readership, and it spread from there into the usual dating of western Europe. Accounts differ on how far the count was used before Bede, so his history is best taken as its wide-scale use rather than its first appearance.",
    ),

)
