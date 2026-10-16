package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1361 to 1369: a planetary clock of Padua, an organ of Halberstadt and the imperial kilns of Jingdezhen. */
val db68Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 68,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Giovanni de' Dondi's astrarium, a planetary clock finished in 1364",
        originalTitle = "Astrarium",
        author = "Giovanni de' Dondi",
        year = 1364,
        summary = "Giovanni de' Dondi's astrarium is a planetary clock that shows the motions of the sun, the moon and the five planets, and it was finished in 1364.",
        details = "The completion date of 1364 is the usual one, and Padua is the place of its making. Sources give different start dates for the work, with one account placing the start as early as 1348, so the span of construction is approximate.",
    ),

    entry(
        databaseId = 68,
        kind = Kind.DEVICE,
        section = "Organs",
        title = "The Halberstadt cathedral organ, completed in 1361",
        originalTitle = "Domorgel Halberstadt",
        author = "The priest Nicolas Faber",
        year = 1361,
        summary = "The organ of Halberstadt cathedral is dated to 1361 by its inscription, and it is among the earliest large organs with a documented date.",
        details = "The completion date and the maker's name are given in the instrument's inscription, and a later renovation is dated 1495. Claims that it was the first organ of its kind are disputed, and an earlier payment to an organist at Notre-Dame in 1332 is cited against them.",
        rare = true,
    ),

    entry(
        databaseId = 68,
        kind = Kind.DEVICE,
        section = "Kilns",
        title = "The imperial kilns of Jingdezhen, set up for the Ming court in 1369",
        originalTitle = "Jingdezhen imperial kilns",
        author = "The court of the Hongwu emperor",
        year = 1369,
        summary = "The Ming court set up imperial kilns at Jingdezhen in 1369 to make porcelain for the palace and the state.",
        details = "The year 1369 is the one usually given for the court kilns, so the date is approximate, and accounts differ on how early the first court wares were made. The kilns ran under official supervision, and their output is known mainly from later palace records.",
        rare = true,
    ),

)
