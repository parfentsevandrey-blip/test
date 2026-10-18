package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1170 to 1180: one siege engine, the mangonel, dated by an approximate range. */
val db87Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 87,
        kind = Kind.DEVICE,
        section = "Siege engines",
        title = "The mangonel, a sling-arm siege engine named in twelfth-century chronicles",
        originalTitle = "Mangonel",
        author = "Siege engineers and chroniclers of the period",
        year = 1175,
        summary = "The mangonel was a siege engine that threw stones from a sling on a swinging arm, and twelfth-century chronicles use the name for engines of several designs.",
        details = "The record gives the range 1170 to 1180 as an approximate date, because the name was used loosely for many engines across the twelfth and thirteenth centuries, so no single decade marks its arrival. Chronicles of the later twelfth century use the name in accounts of sieges, and the year 1175 stands for the decade rather than for a first use.",
        rare = true,
    ),

)
