package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1500 to 1510: Bembo's Petrarch, Calepino's dictionary, Reuchlin's Hebrew grammar, Bembo's Italian dialogues, a five-version psalter and a school book of Latin sentences. */
val db54Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 54,
        kind = Kind.LANGUAGE,
        section = "Italian",
        title = "Bembo's Petrarch of 1501, the Italian poems",
        originalTitle = "Le cose volgari di Messer Francesco Petrarcha",
        author = "Pietro Bembo",
        year = 1501,
        summary = "Bembo's edition of Petrarch's Italian poems, printed by Aldus at Venice in 1501, gave the poems a settled text for the readers who followed it.",
        details = "The volume collected Petrarch's vernacular poems, and later printings of the poet often followed its order. The book shows how the Italian of the fourteenth century was set in print for a new reading public.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Calepino's Latin dictionary of 1502",
        originalTitle = "Dictionarium",
        author = "Ambrogio Calepino",
        year = 1502,
        summary = "Calepino's Latin dictionary, first printed at Reggio nell'Emilia in 1502, was reprinted so often that it became the common Latin dictionary of its age.",
        details = "The first edition was a folio volume printed at Reggio in 1502 by Dionysius Bertochus, with alphabetical headwords and Latin explanations. Later printings added other languages to the explanations, and the book was reissued many times in the sixteenth century.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "Reuchlin's Hebrew grammar and lexicon of 1506",
        originalTitle = "De rudimentis Hebraicis",
        author = "Johannes Reuchlin",
        year = 1506,
        summary = "Reuchlin's book of 1506 gave Latin readers a grammar and a short dictionary of Hebrew, set out with the Hebrew letters for study.",
        details = "It was printed at Pforzheim in 1506 by Thomas Anshelm, and it is often named as one of the first Hebrew grammars written for Christian scholars. Accounts differ on how to rank it, since earlier Christian writers had already treated Hebrew in Latin.",
        rare = true,
    ),

    entry(
        databaseId = 54,
        kind = Kind.LANGUAGE,
        section = "Italian",
        title = "Bembo's dialogues on love in Italian",
        originalTitle = "Gli Asolani",
        author = "Pietro Bembo",
        year = 1505,
        summary = "Bembo's Asolani, printed by Aldus at Venice in 1505, are dialogues on love written in Italian, a choice that helped make the vernacular a literary language.",
        details = "The dialogues are set at Asolo at the court of Caterina Cornaro, and Aldus printed the book in octavo. Bembo later argued in his Prose of 1525 that Italian should follow the writers of the fourteenth century, so the Asolani belong to that debate.",
        rare = true,
    ),

    entry(
        databaseId = 54,
        kind = Kind.LANGUAGE,
        section = "Psalms",
        title = "Lefevre d'Etaples's five Latin Psalters side by side",
        originalTitle = "Psalterium quintuplex",
        author = "Jacques Lefevre d'Etaples",
        year = 1509,
        summary = "Lefevre d'Etaples printed the Psalms at Paris in 1509 in five Latin versions set side by side, so that readers could compare their wording at a glance.",
        details = "The volume is named the fivefold Psalter, and its columns let readers see how the Latin of the Psalms differed from one version to the next. The book belongs to the early humanist work on the sacred texts that was done at Paris in these years.",
        rare = true,
    ),

    entry(
        databaseId = 54,
        kind = Kind.LANGUAGE,
        section = "Schoolbooks",
        title = "Stanbridge's Vulgaria, Latin sentences for schoolboys",
        originalTitle = "Vulgaria",
        author = "John Stanbridge",
        year = 1509,
        summary = "Stanbridge's Vulgaria gave schoolboys short English sentences to turn into Latin, and it was printed in London by Wynkyn de Worde in 1509.",
        details = "The book was meant for daily practice in Latin and in everyday speech, and later editions followed the first. It shows how English boys were taught Latin in the early Tudor schools, with sentences drawn from common life.",
        rare = true,
    ),

)
