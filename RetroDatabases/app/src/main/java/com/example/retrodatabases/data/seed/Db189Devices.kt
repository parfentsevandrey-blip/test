package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 150 to 160: a Roman guest token split in two, placed at 157 with the anchor of Galen's post at Pergamon. */
val db189Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 189,
        kind = Kind.DEVICE,
        section = "Tokens",
        title = "The Roman tessera hospitalis, a split token that proved a guest-host bond",
        originalTitle = "Tessera hospitalis",
        author = "Roman families of guest friendship",
        year = 157,
        summary = "A tessera hospitalis was a token split in two, so that a guest and a host could each keep a half and fit the halves together at a later meeting.",
        details = "The sources do not date this token; 157 is the placed year, the date of the anchor, Galen's post as physician at Pergamon, and not the date of any token. Surviving tokens are often bronze and some show clasped hands, and each pair of halves fitted only together, which made the token a check of identity.",
        rare = true,
    ),

)
