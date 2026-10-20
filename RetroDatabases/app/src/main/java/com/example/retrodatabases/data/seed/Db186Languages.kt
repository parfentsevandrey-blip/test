package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 180 to 190: the Xiping stone classics, cut on steles at Luoyang, and Irenaeus's Against Heresies, a Greek work known in Latin. */
val db186Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 186,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Xiping stone classics, Confucian texts cut on steles at Luoyang",
        originalTitle = "Xiping shijing",
        author = "Cai Yong and his colleagues",
        year = 183,
        summary = "The Xiping stone classics were Confucian texts cut on stone steles at the Imperial Academy near Luoyang, finished in 183 after a request made in 175.",
        details = "Cai Yong and his colleagues asked Emperor Ling for the stones in 175, fearing that rival factions would tamper with the classics. Accounts differ on the number of steles, which is given as 46 in some and 48 in others, and one account ties the loss of the stones to 190, when Dong Zhuo burned the palaces of Luoyang.",
        rare = true,
    ),

    entry(
        databaseId = 186,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Irenaeus's Against Heresies, five books against the falsely named knowledge",
        originalTitle = "Elenchos kai anatrope tes pseudonymou gnoseos",
        author = "Irenaeus of Lyon",
        year = 180,
        summary = "Irenaeus, bishop of Lyon, wrote his Against Heresies in five books about 180 to refute teachers who claimed a false knowledge.",
        details = "The Greek original is lost apart from fragments, and the work survives mainly in a Latin translation. Its third book argues that there are four gospels, neither more nor fewer, and the date of about 180 is the one usually given.",
        rare = true,
    ),

)
