package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 90 to 100, placed at 97: the Chinese drum carriage that struck a drum at each li of a journey, with its anchor named. */
val db195Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 195,
        kind = Kind.DEVICE,
        section = "Counting",
        title = "The drum carriage of China, which struck a drum at each li of a journey",
        originalTitle = "Jili guche",
        author = "Han craftsmen, with Zhang Heng or Ma Jun named in some accounts",
        year = 97,
        summary = "A drum carriage counted the li of a road through its gearing, sounding a drum at each li and a bell at every ten li.",
        details = "No source dates the first drum carriage: the descriptions of its drum and bell that are usually cited are of the third century, and the attribution to Zhang Heng or to the engineer Ma Jun, who lived about 200 to 265, is disputed. This record is placed at 97, the year in which Ban Chao sent the envoy Gan Ying toward Daqin according to the Hou Hanshu, and the year is the anchor used for placement, not the date of any carriage.",
        rare = true,
    ),

)
