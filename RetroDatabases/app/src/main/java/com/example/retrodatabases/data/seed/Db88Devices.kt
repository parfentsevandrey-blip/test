package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1160 to 1170: gunpowder bombs thrown from warships at the battle of Caishi, and the Woodbridge tide mill. */
val db88Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 88,
        kind = Kind.DEVICE,
        section = "Weapons",
        title = "Gunpowder-and-lime thunderclap bombs thrown at the battle of Caishi, 1161",
        originalTitle = "Pili pao",
        author = "The Song navy and its engineers",
        year = 1161,
        summary = "At the battle of Caishi on 26 and 27 November 1161, Song forces threw gunpowder bombs from trebuchets mounted on their warships.",
        details = "Accounts differ on the filling, with some naming gunpowder and lime and others adding iron scraps or arsenic. Whether this was the first battlefield use of gunpowder bombs is not settled, so the battle is cited as an early record rather than a proven first.",
        rare = true,
    ),

    entry(
        databaseId = 88,
        kind = Kind.DEVICE,
        section = "Water power",
        title = "The Woodbridge tide mill, first documented on its site about 1170",
        originalTitle = "Woodbridge Tide Mill",
        author = "The canons of Woodbridge Priory",
        year = 1170,
        summary = "The earliest documented mill on the Woodbridge site dates to about 1170, when a twelfth-century charter records a tithe gift to the priory's canons.",
        details = "A tide mill grinds grain with water held in a pond on the flood and released to turn the wheel on the ebb. Tide mills appear in the Domesday survey of 1086, so the date about 1170 marks the first record of this site rather than the first tide mill in England.",
        rare = true,
    ),

)
