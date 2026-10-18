package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages and texts of 1070 to 1076: a Latin church history, a Sanskrit tale collection, a Slavonic anthology, a Greek advice book, a Turkic poem and a Chinese essay. */
val db97Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 97,
        kind = Kind.LANGUAGE,
        section = "Germany and the north",
        title = "Adam of Bremen's history of the bishops of Hamburg, written in Latin",
        originalTitle = "Gesta Hammaburgensis ecclesiae pontificum",
        author = "Adam of Bremen",
        year = 1073,
        summary = "Adam of Bremen wrote a Latin history of the Hamburg church and its bishops in four books, begun about 1073 and finished by about 1076.",
        details = "The fourth book describes the lands of the far north and names a land it calls Vinland. Adam was a canon at Bremen, and his work is one of the main Latin texts on the Baltic and Scandinavia in the 1070s.",
        rare = true,
    ),

    entry(
        databaseId = 97,
        kind = Kind.LANGUAGE,
        section = "Kashmir",
        title = "Somadeva's Ocean of the Streams of Story, a Sanskrit collection of tales",
        originalTitle = "Kathasaritsagara",
        author = "Somadeva",
        year = 1070,
        summary = "Somadeva composed the Kathasaritsagara, a Sanskrit collection of linked tales, in Kashmir about 1070 for the queen Suryamati.",
        details = "The collection puts many stories inside one frame story and draws on a lost Prakrit work attributed to Gunadhya. Its date is given as about 1070, within the reign of queen Suryamati of Kashmir.",
    ),

    entry(
        databaseId = 97,
        kind = Kind.LANGUAGE,
        section = "Kievan Rus",
        title = "The Izbornik of Sviatoslav of 1073, a dated Old Russian anthology",
        originalTitle = "Izbornik Svyatoslava 1073",
        author = "Scribes for Prince Sviatoslav Yaroslavich",
        year = 1073,
        summary = "The Izbornik of Sviatoslav is an anthology of translated Byzantine writings in Old Russian, copied in 1073 for Prince Sviatoslav Yaroslavich.",
        details = "Its colophon gives the year 1073, and a second anthology was made for the same prince in 1076. The texts are Slavonic translations of Byzantine writings on conduct, history and scripture.",
        rare = true,
    ),

    entry(
        databaseId = 97,
        kind = Kind.LANGUAGE,
        section = "Byzantium",
        title = "Kekaumenos's Strategikon, a Greek book of advice by a Byzantine general",
        originalTitle = "Strategikon of Kekaumenos",
        author = "Kekaumenos",
        year = 1075,
        summary = "Kekaumenos, a Byzantine general of partly Armenian descent, wrote a Greek book of advice on command, household and social life between 1075 and 1078.",
        details = "The book mixes military advice with stories from the author's own career, and its later sections cover running a household, raising children and keeping social ties. Its date is usually given as 1075 to 1078, though some accounts place the finished text at about 1078.",
        rare = true,
    ),

    entry(
        databaseId = 97,
        kind = Kind.LANGUAGE,
        section = "Central Asia",
        title = "Yusuf Khass Hajib's Kutadgu Bilig, a Turkic poem of rule and conduct",
        originalTitle = "Kutadgu Bilig",
        author = "Yusuf Khass Hajib of Balasagun",
        year = 1070,
        summary = "Yusuf Khass Hajib finished the Kutadgu Bilig, a long Turkic poem of advice on rule and conduct, at Kashgar in about 1069 to 1070.",
        details = "He presented the poem to the prince of Kashgar, who gave him the court title Khass Hajib. Its dates vary between 1068 and 1070, so the work is given here as finished in 1069 to 1070.",
        rare = true,
    ),

    entry(
        databaseId = 97,
        kind = Kind.LANGUAGE,
        section = "China",
        title = "Zhang Zai's Western Inscription, an essay on Heaven as father and Earth as mother",
        originalTitle = "Xi Ming, the Western Inscription",
        author = "Zhang Zai",
        year = 1076,
        summary = "Zhang Zai wrote the Western Inscription in 1076, a short Chinese essay that treats Heaven as father, Earth as mother and all people as kin.",
        details = "The essay was placed in the author's study, and it became one of the best known short texts of Song Confucian thought. It argues that all people are kin, so care for the weak is a duty of kinship.",
        rare = true,
    ),

)
