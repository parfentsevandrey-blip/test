package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 550 to 560: the drawloom for figured silk, undated in Byzantium and placed at 552, the year of the silkworm eggs. */
val db149Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 149,
        kind = Kind.DEVICE,
        section = "Byzantine",
        title = "The drawloom, a loom that lifts warp threads by pattern for figured silk, undated in Byzantium and placed at 552",
        originalTitle = "drawloom",
        author = "Unknown weavers",
        year = 552,
        summary = "A drawloom lets a weaver lift warp threads by pattern, so that figured silk can be woven, and its first use in the Byzantine silk workshops is not dated.",
        details = "The sources do not date this device; 552 is the year of the anchor, the silkworm eggs story, not of the making of any loom. The record places the drawloom beside that anchor only because figured silk is woven on such looms, and it makes no claim about when one first reached Constantinople.",
        rare = true,
    ),

)
