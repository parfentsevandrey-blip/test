package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 590 to 600: Augustine's mission from Rome to Kent in 597 and the chancery register that copied Gregory the Great's letters from 590. */
val db145Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 145,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "Augustine's mission from Rome to Kent in 597, the first Roman mission to the English",
        originalTitle = "Gregorian mission to the Angli",
        author = "Augustine and Gregory the Great",
        year = 597,
        summary = "Augustine, sent by Pope Gregory the Great, landed in Kent in 597 and began the first Roman mission to the English at the court of King AEthelberht.",
        details = "Gregory sent the mission in 596, and Bede dates the landing on the Isle of Thanet to 597 in a history finished in 731. The mission set up the see of Canterbury, the seat of the archbishop of the English church.",
    ),

    entry(
        databaseId = 145,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "Gregory the Great's papal register, yearly papyrus volumes that copied his letters from 590 to 604",
        originalTitle = "Registrum epistolarum",
        author = "Gregory the Great's chancery",
        year = 590,
        summary = "Gregory the Great's chancery kept yearly papyrus volumes that copied his letters from 590 to 604, a series that later editions rebuild from extracts.",
        details = "The original volumes are lost, and the text survives in eighth-century extracts, which the Ewald and Hartmann edition of 1887 to 1899 used to rebuild the register. Editions give different totals of letters, from about 680 to over 850, so the count depends on how the books are numbered.",
        rare = true,
    ),

)
