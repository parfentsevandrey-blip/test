package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1101 to 1103: a Hebrew dictionary of the Talmud and a Song building manual with standard measures. */
val db94Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 94,
        kind = Kind.TOOL,
        section = "Talmud",
        title = "Nathan ben Jehiel's Arukh, a dictionary of the words of the Talmud",
        originalTitle = "Arukh",
        author = "Nathan ben Jehiel of Rome",
        year = 1101,
        summary = "The Arukh is a Hebrew dictionary of the hard words and phrases of the Talmud, completed in 1101 by Nathan ben Jehiel of Rome.",
        details = "Accounts date its completion to 1101, and reference works give the author's lifespan in different ways. An earlier Talmudic 'Aruk, tied to a ninth-century scholar of Pumbedita, survives only in fragments and is a different work.",
        rare = true,
    ),

    entry(
        databaseId = 94,
        kind = Kind.TOOL,
        section = "Building",
        title = "Li Jie's Yingzao Fashi, the Song building manual of standard measures",
        originalTitle = "Yingzao fashi",
        author = "Li Jie",
        year = 1103,
        summary = "The Yingzao Fashi is a Song building manual in 34 chapters that gives standard measures, design rules and building methods, printed in 1103 under Emperor Huizong.",
        details = "Li Jie was the chief compiler, and accounts differ on when he received the task: one review dates the assignment to 1097, while another names the emperor Shenzong as the ruler who ordered the work. The manual was lost after the early 1500s and was rediscovered in 1919 through a manuscript copy.",
    ),

)
