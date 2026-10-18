package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1132 and 1139: the fire lance at the siege of De'an and the crossbow barred by the Second Lateran Council. */
val db91Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 91,
        kind = Kind.DEVICE,
        section = "Weapons",
        title = "The fire lance at the siege of De'an, the earliest recorded battle use in 1132",
        originalTitle = "Huoqiang",
        author = "The garrison of De'an in Song China",
        year = 1132,
        summary = "The fire lance, a tube of gunpowder mounted on a lance, appears in the earliest recorded battle use at De'an in 1132.",
        details = "Chen Gui's biography in the Song official history reports that sixty of the town's defenders carried fire lances out through a western gate to wreck the attackers' siege engines. The weapon is older than that record, with origins placed in the tenth to twelfth centuries, so 1132 dates the earliest recorded battle use rather than the invention.",
        rare = true,
    ),

    entry(
        databaseId = 91,
        kind = Kind.DEVICE,
        section = "Weapons",
        title = "The crossbow, which the Second Lateran Council of 1139 barred against Christians",
        originalTitle = "Crossbow",
        author = "No single author",
        year = 1139,
        summary = "The crossbow, a bow fixed to a stock and fired by a trigger, was barred against Christians by a canon of the Second Lateran Council in 1139.",
        details = "The canon shows that the crossbow was in wide enough use by the twelfth century for the church to restrict its use against Christians. The weapon itself predates the council, so 1139 records a church rule about the device rather than its first use.",
    ),

)
