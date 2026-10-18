package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1185: a windmill named at Weedley in Yorkshire and a deep well with animal-turned lifts in the Cairo Citadel. */
val db86Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 86,
        kind = Kind.DEVICE,
        section = "Windmills",
        title = "The windmill at Weedley in Yorkshire, named in a list of 1185",
        originalTitle = "Weedley windmill",
        author = "Unknown millwrights of Yorkshire",
        year = 1185,
        summary = "A windmill at Weedley in Yorkshire is named in an entry of 1185, and it was once taken as the oldest English windmill on record.",
        details = "The entry sits in an inventory of Templar property, and a 1988 report by the Mills Research Group places the mill before 1185. Edward Kealey's book Harvesting the Air (1987) dates England's first windmills to before 1137, so the Weedley entry is not the earliest record.",
        rare = true,
    ),

    entry(
        databaseId = 86,
        kind = Kind.DEVICE,
        section = "Water lifts",
        title = "Joseph's Well in the Cairo Citadel, a deep well with animal-turned lifts",
        originalTitle = "Bir Yusuf",
        author = "Builders of Saladin's citadel",
        year = 1185,
        summary = "Joseph's Well in the Cairo Citadel is a deep well from Saladin's time, with wheels on two levels that animals turned to raise its water.",
        details = "An official Egyptian monuments page places the digging within 1176 to 1193 and credits Saladin, who gave the work to his vizier, so 1185 is a midpoint rather than a settled date. An 1849 travel volume says Saladin uncovered an older well, and an 1837 account, Incidents of Travel, names a buffalo rather than cattle as the animal at the wheel.",
        rare = true,
    ),

)
