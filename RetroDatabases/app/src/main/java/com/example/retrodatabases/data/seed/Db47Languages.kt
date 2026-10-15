package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1570 to 1580: a Greek thesaurus, a Nahuatl dictionary, the first book in Irish type, a spelling reform, a gazetteer of place names and a Dutch word book. */
val db47Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 47,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Estienne's Thesaurus of the Greek tongue, 1572",
        originalTitle = "Thesaurus Graecae linguae",
        author = "Henri Estienne",
        year = 1572,
        summary = "Estienne's Thesaurus of 1572 gave Greek words with quotations from Greek writers, and it stayed the main large Greek dictionary until the nineteenth century.",
        details = "The Thesaurus was published in 1572 in folio volumes, and each word was set out with examples that showed how the classical authors used it. It remained the chief large dictionary of Greek for more than two centuries.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.LANGUAGE,
        section = "Nahuatl",
        title = "Molina's Nahuatl and Spanish dictionary of 1571",
        originalTitle = "Vocabulario en lengua mexicana y castellana",
        author = "Alonso de Molina",
        year = 1571,
        summary = "Molina's dictionary of 1571 gave the words of Nahuatl, the language of Mexico, with Spanish meanings for the friars who worked among its speakers.",
        details = "The complete two-part work was printed at Mexico City in 1571, and its Nahuatl-to-Spanish half bears the title used here, after a Spanish-to-Nahuatl part had appeared in 1555. The dictionary was made for missionaries who needed the language for their work, and it is one of the earliest printed dictionaries of an American language.",
        rare = true,
    ),

    entry(
        databaseId = 47,
        kind = Kind.LANGUAGE,
        section = "Irish",
        title = "The first book set in Irish type, the Dublin primer of 1571",
        originalTitle = "Aibidil Gaoidheilge & Caiticiosma",
        author = "John Kearney",
        year = 1571,
        summary = "The Irish alphabet and catechism of 1571 is the earliest book in the Irish language printed in Ireland, and the first to be set in Gaelic type.",
        details = "The author is named as John Kearney, Sean O Cearnaigh in Irish, who also drew on Latin and English sources for the Irish text, and the book was a Protestant primer for teaching religion in the language of the people. Accounts differ on who printed it, since the sources name different presses and patrons, and a facsimile of the 1571 text was issued in 1994.",
        rare = true,
    ),

    entry(
        databaseId = 47,
        kind = Kind.LANGUAGE,
        section = "Spelling",
        title = "Bullokar's Booke at large for a new English spelling, 1580",
        originalTitle = "Booke at large for the amendment of orthographie for English speech",
        author = "William Bullokar",
        year = 1580,
        summary = "Bullokar's book of 1580 proposed a new spelling for English, with extra letters so that each sound of speech had its own sign.",
        details = "The book was printed in London in 1580, and Bullokar followed it with a short grammar of English in 1586. Earlier writers, among them John Hart in 1569, had argued for a spelling that followed the sounds, and the extra letters of such schemes were never adopted in general use.",
        rare = true,
    ),

    entry(
        databaseId = 47,
        kind = Kind.LANGUAGE,
        section = "Place names",
        title = "Ortelius's Synonymia of place names, 1578",
        originalTitle = "Synonymia geographica",
        author = "Abraham Ortelius",
        year = 1578,
        summary = "Ortelius's Synonymia of 1578 listed the names of places in their several forms and languages, so that a reader could match an old name with a modern one.",
        details = "The work was printed at Antwerp in 1578 and was written by the mapmaker who had produced his atlas of 1570. Its lists gave the Latin, Greek and vernacular forms of many names, and they helped readers of old texts to find the place they meant.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Kiliaan's Etymologicum of the Dutch tongue, 1574",
        originalTitle = "Etymologicum Teutonicae linguae",
        author = "Cornelis Kiliaan",
        year = 1574,
        summary = "Kiliaan's word book of 1574 set out Dutch words beside Latin equivalents and the forms they were thought to come from, and it was one of the first large dictionaries of Dutch.",
        details = "The book was printed at Antwerp in 1574, and its author, Cornelis van Kiel, Latinised his name as Kiliaan. Its derivations were often mistaken by later standards, but later makers of Dutch word books were glad to draw on its material.",
    ),

)
