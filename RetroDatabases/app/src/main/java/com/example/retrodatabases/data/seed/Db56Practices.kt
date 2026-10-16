package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1480 to 1490: a bookplate woodcut, colouring of woodcuts, proof correction, abbreviation marks and sewing printed quires. */
val db56Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 56,
        kind = Kind.PRACTICE,
        section = "Ownership",
        title = "Ownership woodcuts pasted into books, the first bookplate of 1480",
        originalTitle = "Ex libris",
        author = "Hilprand Brandenburg, Carthusian of Buxheim",
        year = 1480,
        summary = "A Carthusian monk at Buxheim is credited with the earliest known printed bookplate, a woodcut of 1480 that marked his books as his own and was pasted into each one.",
        details = "Sources give the woodcut the date 1480 and name the monk Hilprand Brandenburg, though some accounts spell his name Hildebrand. One exhibit dates a Brandenburg plate to the 1460s, and other accounts place the earliest printed examples in Germany in the 1400s, so accounts differ on the first plate.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.PRACTICE,
        section = "Illustration",
        title = "Colouring printed woodcuts by hand",
        originalTitle = "Coloured woodcut",
        author = "Illuminators and colourists of the 1480s",
        year = 1485,
        summary = "Printed woodcuts were coloured by hand with paint, so that each copy of an illustrated book could differ from the next in its colours.",
        details = "A colourist worked from a model sheet and applied the colours to each copy, which made the price of a coloured copy higher than a plain one. The year given is approximate, because the practice ran through the whole decade in several cities.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.PRACTICE,
        section = "Printing house",
        title = "Proof sheets checked against the copy",
        originalTitle = "Proof sheet",
        author = "Correctors of the printing houses",
        year = 1484,
        summary = "A corrector read each proof sheet against the manuscript copy and marked the errors for the compositor to reset before the run began.",
        details = "Correctors were often scholars employed by the printer, and their marks were written in the margins in a set of conventional signs. The year given is approximate, and accounts differ on how many shops in the 1480s kept a paid corrector.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.PRACTICE,
        section = "Writing",
        title = "Abbreviation marks over letters in printed Latin",
        originalTitle = "Nota abbreviationis",
        author = "Printers of Latin texts",
        year = 1480,
        summary = "Printed Latin used a stroke over a letter to stand for a missing letter or syllable, copying the shorthand of the manuscripts that printers followed.",
        details = "The marks saved space on the line and kept the look of the manuscript page. Accounts differ on how consistently the marks were used from one shop to the next, and the year given is approximate.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.PRACTICE,
        section = "Binding",
        title = "Sewing printed quires onto cords",
        originalTitle = "Sewing frame",
        author = "Binders of the 1480s",
        year = 1485,
        summary = "Binders sewed the printed quires together through their folds and onto cords, and a sewing frame held the cords taut while the thread went through.",
        details = "The quires were gathered in their printed order before sewing, and the cords were then laced into the boards of the binding. The year given is approximate, since sewing on cords was the common method across the decade.",
    ),

)
