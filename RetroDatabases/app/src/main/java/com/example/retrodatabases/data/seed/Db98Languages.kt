package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1060 to 1067: a Japanese diary, a Chinese dynastic history, a Latin poem on Hastings and a Latin life of Edward the Confessor. */
val db98Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 98,
        kind = Kind.LANGUAGE,
        section = "Japanese prose",
        title = "The Sarashina Diary, a Japanese account of a woman's journeys and later years",
        originalTitle = "Sarashina nikki",
        author = "A daughter of Sugawara no Takasue",
        year = 1060,
        summary = "The Sarashina Diary is a memoir in Japanese kana, usually dated to about 1060, that recalls the author's girlhood journey from the eastern provinces to the capital and her later years.",
        details = "Its author is usually taken to be a daughter of Sugawara no Takasue, a provincial official, and the diary is one of the best-known works of Heian women's prose. The date of about 1060 is the usual estimate for its writing, and the year given here is that estimate.",
    ),

    entry(
        databaseId = 98,
        kind = Kind.LANGUAGE,
        section = "Chinese histories",
        title = "The New History of the Tang, a Song dynastic history finished in 1060",
        originalTitle = "Xin Tangshu",
        author = "Ouyang Xiu, Song Qi and their team",
        year = 1060,
        summary = "The New History of the Tang, a dynastic history of 225 chapters, was finished in 1060 under Ouyang Xiu and Song Qi after a project begun in 1044 at the order of Emperor Renzong.",
        details = "The work revised the older Tang history of the 940s, and Ouyang Xiu cut back many myth-laden accounts of the emperors while adding sections of more practical value. Its plan of annals, treatises, tables and biographies follows the arrangement that Sima Qian set in the Shiji.",
    ),

    entry(
        databaseId = 98,
        kind = Kind.LANGUAGE,
        section = "Latin verse",
        title = "The Carmen on the battle of Hastings, a Latin poem praising the Norman victory",
        originalTitle = "Carmen de Hastingae Proelio",
        author = "Guy, bishop of Amiens, to whom the poem is attributed",
        year = 1067,
        summary = "The Carmen de Hastingae Proelio is a Latin poem that presents the battle of Hastings as a Norman triumph, written about 1067 and attributed to Guy, bishop of Amiens.",
        details = "It is one of the earliest accounts of the battle, written within about a year of it. The attribution to Guy and the date of about 1067 are the usual ones.",
        rare = true,
    ),

    entry(
        databaseId = 98,
        kind = Kind.LANGUAGE,
        section = "Latin lives",
        title = "The Latin life of King Edward who rests at Westminster, written in the 1060s",
        originalTitle = "Vita Aedwardi Regis qui apud Westmonasterium requiescit",
        author = "An anonymous writer",
        year = 1065,
        summary = "The Vita Aedwardi Regis is a Latin life of Edward the Confessor, written between about 1065 and 1067, that presents the king as a holy ruler buried at his church in Westminster.",
        details = "The author is unknown, and the work was written in the years around Edward's death in January 1066. Its date is given as between about 1065 and 1067, and the year 1065 is used here as the first of those years.",
        rare = true,
    ),

)
