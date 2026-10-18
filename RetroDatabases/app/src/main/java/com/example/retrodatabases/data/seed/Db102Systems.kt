package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1020 to 1030: the Jiaozi office that took over Sichuan's paper money in 1023. */
val db102Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 102,
        kind = Kind.SYSTEM,
        section = "Money",
        title = "The Jiaozi office of Sichuan, a state bureau for paper money from 1023",
        originalTitle = "The Jiaozi office of Sichuan, a state bureau for paper money from 1023",
        author = "Song government of Sichuan",
        year = 1023,
        summary = "In 1023 the authorities of Sichuan took over the private paper notes called jiaozi and set up a Jiaozi office to issue them.",
        details = "Sixteen major Sichuan firms had formed a note bank called jiaozi hu, and bankruptcies among several of them led the state to take over production. Accounts differ on the date, since a Chengdu conference dates the founding of the Yizhou bureau to 12 January 1024.",
        rare = true,
    ),

)
