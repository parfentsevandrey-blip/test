package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1200 to 1210: two German epics and a romance, a Persian verse romance of Alexander, a French play and a Latin grammar in verse. */
val db84Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 84,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "The Nibelungenlied, a German heroic epic of about 1200",
        originalTitle = "Diu Nibelunge not",
        author = "Anonymous",
        year = 1200,
        summary = "The Nibelungenlied is a Middle High German heroic epic of revenge and downfall, composed in the years around 1200 and copied in manuscripts from the early thirteenth century.",
        details = "The poem is anonymous, and its earliest manuscripts are from the first decades of the thirteenth century, so the year 1200 is an estimate for its composition rather than a recorded date. Scholars differ on where it was composed, and the estimate rests on the poem's language and style.",
    ),

    entry(
        databaseId = 84,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "Wolfram's Parzival, a German romance of the Grail",
        originalTitle = "Parzival",
        author = "Wolfram von Eschenbach",
        year = 1205,
        summary = "Wolfram von Eschenbach's Parzival is a German romance of a knight's search for the Grail, composed in the years about 1200 to 1210.",
        details = "The poem is divided into sixteen books, and its usual date of composition is about 1200 to 1210, although accounts differ on the order in which its parts were finished. The year 1205 is the middle of that range and is used here as a round date.",
    ),

    entry(
        databaseId = 84,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "Gottfried's Tristan, a German romance of about 1210",
        originalTitle = "Tristan",
        author = "Gottfried von Strassburg",
        year = 1210,
        summary = "Gottfried von Strassburg's Tristan is a German romance of love and deceit, written about 1210 and left unfinished when its author died.",
        details = "The poem retells the Tristan story from earlier Anglo-Norman and German versions, and it breaks off before the story reaches its end. The date of Gottfried's death is not known, so the year 1210 is an estimate, and accounts give a range of about 1200 to 1215.",
    ),

    entry(
        databaseId = 84,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Nizami's Sharafnama, the first part of a Persian romance of Alexander",
        originalTitle = "Sharafnama",
        author = "Nizami Ganjavi",
        year = 1201,
        summary = "Nizami's Sharafnama is the first part of his Persian verse romance of Alexander the Great, completed about 1201.",
        details = "The Sharafnama opens the Iskandarnama, the long poem on Alexander, and the year 1201 is the one usually given for the work. Accounts differ on when the writing began, so 1201 marks the end of the first part rather than its start.",
        rare = true,
    ),

    entry(
        databaseId = 84,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Jean Bodel's play of Saint Nicholas, a French miracle play from Arras",
        originalTitle = "Le Jeu de saint Nicolas",
        author = "Jean Bodel",
        year = 1200,
        summary = "Le Jeu de saint Nicolas is a French play about a miracle of Saint Nicholas, written by Jean Bodel at Arras about 1200.",
        details = "The play tells a miracle of the saint in French verse, and it is one of the earliest surviving French plays of its kind. The date of about 1200 is an estimate from the poet's career and the play's language, and accounts differ on the exact year.",
        rare = true,
    ),

    entry(
        databaseId = 84,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Alexander's Doctrinale, a Latin grammar in verse for schoolboys",
        originalTitle = "Doctrinale puerorum",
        author = "Alexander de Villa Dei",
        year = 1200,
        summary = "The Doctrinale is a Latin grammar in hexameter verse by Alexander de Villa Dei, learned by heart in schools for centuries.",
        details = "Its verses were copied and printed in large numbers, and the poem stayed in use as a school book into the early modern period. Most accounts give its composition as about 1200, but library catalogues give the author's dates as about 1170 to 1250, so the year is an estimate.",
        rare = true,
    ),

)
