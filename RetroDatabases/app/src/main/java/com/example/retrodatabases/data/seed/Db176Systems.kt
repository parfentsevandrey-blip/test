package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 280 to 290: the Jin conquest of Wu in 280, and the traditional arrival of Chinese writing in Japan through Wani in 285. */
val db176Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 176,
        kind = Kind.SYSTEM,
        section = "Empire",
        title = "The Jin conquest of Wu, which ended the Three Kingdoms in 280",
        originalTitle = "Ping Wu",
        author = "The Jin court and its generals",
        year = 280,
        summary = "The Jin conquest of Wu ended the Three Kingdoms and put China under one Jin state, after Sun Hao, the last ruler of Wu, surrendered in 280.",
        details = "Sun Hao surrendered in 280 and the Jin army took Jianye, the capital of Wu. With Wu gone, the separate states of Wei, Shu and Wu had all ended, and China was ruled by one Jin state under the house of Sima.",
    ),

    entry(
        databaseId = 176,
        kind = Kind.SYSTEM,
        section = "Writing",
        title = "The traditional arrival of Chinese writing in Japan through Wani, with the Analects",
        originalTitle = "Wang In",
        author = "Wani, described as a scholar from Baekje",
        year = 285,
        summary = "The Japanese chronicles date the arrival of Chinese writing in Japan to 285, when the scholar Wani is said to have brought the Analects to the court.",
        details = "The Nihon shoki places Wani's arrival in the sixteenth year of the emperor Ojin, which is 285 in the traditional reckoning, and says that the crown prince studied under him. The Kojiki gives ten volumes of the Analects, and modern scholars doubt the account, with some of them placing any real spread of Chinese writing in the fifth century.",
        rare = true,
    ),

)
