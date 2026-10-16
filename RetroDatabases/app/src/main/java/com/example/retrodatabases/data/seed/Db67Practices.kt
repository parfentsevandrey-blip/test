package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1375 to 1379: a thirty-day isolation at Ragusa, a 1379 cipher ledger, the 1377 poll tax, the Goryeo gunpowder office and the muwaqqit's post at Damascus. */
val db67Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 67,
        kind = Kind.PRACTICE,
        section = "Health",
        title = "Ragusa's thirty-day isolation of travellers from plague ports",
        originalTitle = "Trentino",
        author = "The council of Ragusa",
        year = 1377,
        summary = "In 1377 the council of Ragusa on the Dalmatian coast required ships and travellers from plague areas to wait thirty days before entering the city.",
        details = "The thirty-day rule, called the trentino, was later lengthened to forty days in Venetian practice, and the word quarantine comes from that forty-day rule. Accounts differ on whether Ragusa was the first port to set such a rule, so 1377 is the year usually given.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.PRACTICE,
        section = "Ciphers",
        title = "The cipher ledger of Gabriele de Lavinde, 1379",
        originalTitle = "Cipher ledger",
        author = "Gabriele de Lavinde",
        year = 1379,
        summary = "Gabriele de Lavinde's cipher ledger of 1379 is among the earliest surviving European books of ciphers, with letter substitutions and word codes for papal letters.",
        details = "The ledger is usually linked with the papal curia during the schism, most often with Clement VII at Avignon. Accounts differ on the patron, so the link to Clement VII is given as the usual attribution.",
        rare = true,
    ),

    entry(
        databaseId = 67,
        kind = Kind.PRACTICE,
        section = "Records",
        title = "The English poll tax of 1377, a groat on each adult",
        originalTitle = "The poll tax of 1377",
        author = "The English Parliament",
        year = 1377,
        summary = "The English poll tax of 1377 charged a flat groat on each adult, so every township had to list its people, which made an early count of taxpayers.",
        details = "Parliament granted the tax in 1377, and the groat was four pence, levied on people over fourteen years of age. The returns were drawn up locally, so the lists are an early count of population in England.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.PRACTICE,
        section = "Gunpowder",
        title = "The Goryeo office for gunpowder weapons, set up in 1377",
        originalTitle = "Hwat'ong",
        author = "Choe Mu-seon",
        year = 1377,
        summary = "The Goryeo court opened an office for gunpowder weapons in 1377 on the urging of Choe Mu-seon, and the office made cannons, rocket arrows and fire tubes.",
        details = "Accounts differ on how Choe learned to make gunpowder, with one account sending him to Yuan China and another having a merchant teach him. The year 1377 is the one given for the office, and the Korean word hwat'ong names the fire tube made there.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.PRACTICE,
        section = "Timekeeping",
        title = "The muwaqqit's post at the Umayyad Mosque, held by Ibn al-Shatir",
        originalTitle = "Muwaqqit",
        author = "Ibn al-Shatir",
        year = 1375,
        summary = "The muwaqqit at the Umayyad Mosque in Damascus worked out the hours of prayer and the direction of Mecca, and Ibn al-Shatir held that post, dying in 1375.",
        details = "The muwaqqit used astronomical tables and instruments, and Ibn al-Shatir's sundial on the minaret marked the equal hours of prayer. Accounts differ on the years he served in the post, so 1375, the year of his death, is the date used here.",
        rare = true,
    ),

)
