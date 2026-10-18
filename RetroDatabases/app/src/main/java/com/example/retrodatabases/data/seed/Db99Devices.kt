package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Device of 1060: the Kirkdale sundial, dated by the names in its inscription to a range from 1055 to 1065. */
val db99Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 99,
        kind = Kind.DEVICE,
        section = "England",
        title = "The Kirkdale sundial, an Anglo-Saxon dial whose inscription names Earl Tosti",
        originalTitle = "Kirkdale sundial",
        author = "Orm son of Gamal",
        year = 1060,
        summary = "The Kirkdale sundial in Yorkshire carries an inscription that names Orm son of Gamal, King Edward and Earl Tosti, and those names date it only to a range from 1055 to 1065.",
        details = "The inscription records Orm's purchase of the ruined church of Saint Gregory and its rebuilding, and it gives no calendar year. Tosti held the earldom of Northumbria from 1055, and accounts differ on whether it ended in 1065 or 1066, so this record uses 1060 as the midpoint of the range. The rebuilding of the church is dated to 1055 by some writers and to 1060 by others.",
        rare = true,
    ),

)
