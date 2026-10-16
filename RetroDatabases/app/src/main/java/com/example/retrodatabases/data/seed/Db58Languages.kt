package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1460 to 1470: a Mainz word book, a Latin-German word list, German fables and Bible, Latin letters printed at Paris and a Czech Troy chronicle. */
val db58Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 58,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Balbus's Catholicon, the Latin word book of Mainz",
        originalTitle = "Catholicon",
        author = "Johannes Balbus of Genoa, printed at Mainz",
        year = 1460,
        summary = "Balbus's Latin word book and grammar, finished at Genoa in 1286, was printed at Mainz in 1460 and is among the earliest large books in print.",
        details = "Balbus finished the Catholicon in 1286 as a Latin dictionary with grammar and etymologies. The Mainz edition carries the date 1460, and accounts differ on which printer made it, with some assigning it to Gutenberg's workshop.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "The Eltville Latin-German word list of 1467",
        originalTitle = "Vocabularius ex quo",
        author = "Heinrich and Nikolaus Bechtermunze of Eltville",
        year = 1467,
        summary = "The Latin-German word list printed at Eltville in 1467 gave Latin headwords with German glosses and is one of the earliest such books in print.",
        details = "The first edition is dated 4 November 1467 and runs to about 165 leaves in quarto. Heinrich Bechtermunze died during the printing, and his brother Nikolaus finished the book with Wiegand Spiess von Ortenberg, and later printings followed in 1469 and 1472 while accounts differ on whether the last came in 1476 or 1477.",
        rare = true,
    ),

    entry(
        databaseId = 58,
        kind = Kind.LANGUAGE,
        section = "Fables",
        title = "Boner's Edelstein, German fables with woodcut pictures",
        originalTitle = "Der Edelstein",
        author = "Ulrich Boner, printed by Albrecht Pfister",
        year = 1461,
        summary = "Boner's collection of fables in German, printed at Bamberg in 1461, carried moral tales into print with woodcut pictures.",
        details = "Albrecht Pfister printed the Edelstein at Bamberg in 1461, and it is often named among the earliest German books with woodcut pictures. Boner wrote the fables in the mid-1300s, so the printed text keeps the German of an earlier generation.",
        rare = true,
    ),

    entry(
        databaseId = 58,
        kind = Kind.LANGUAGE,
        section = "Bible",
        title = "Mentelin's German Bible of Strasbourg",
        originalTitle = "Die Bibel in deutscher Sprache",
        author = "Johann Mentelin, printer at Strasbourg",
        year = 1466,
        summary = "Mentelin's German Bible, printed at Strasbourg about 1466, is among the first complete Bibles printed in German.",
        details = "Dates given for the book run from about 1460 to 1466, and accounts differ on which is right. The German text came from older manuscript translations, and the printed copies put the whole Bible into the hands of lay readers in the towns.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.LANGUAGE,
        section = "Letters",
        title = "Gasparinus's Latin letters, the first book printed at Paris",
        originalTitle = "Epistolae",
        author = "Gasparinus of Barzizza, printed by Gering, Crantz and Friburger",
        year = 1470,
        summary = "Model Latin letters by Gasparinus of Barzizza were printed at the Sorbonne in 1470, by most accounts the first book printed in Paris.",
        details = "Ulrich Gering, Martin Crantz and Michael Friburger set up their press in the Sorbonne in 1470, and their first book was this collection of model letters for students. Gasparinus wrote the letters in the first half of the 1400s to teach Latin prose, and the printed edition kept that schoolroom purpose.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.LANGUAGE,
        section = "Vernacular books",
        title = "The Czech Troy chronicle printed at Plzen",
        originalTitle = "Trojanska kronika",
        author = "Guido delle Colonne, in an unnamed Czech translation",
        year = 1468,
        summary = "A Czech version of Guido delle Colonne's history of Troy, printed at Plzen and dated 1468 by its colophon, is traditionally named the first book printed in Czech.",
        details = "Accounts differ on the date, since some place the printing in 1485 to 1486, and the claim to be the first Czech book is disputed. The printer is unknown, and the year used is the one traditionally read from the colophon.",
        rare = true,
    ),

)
