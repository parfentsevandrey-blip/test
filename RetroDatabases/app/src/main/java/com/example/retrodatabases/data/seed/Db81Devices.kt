package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1230 to 1234: a Goryeo metal-type book reported for 1234, and a perpetual-motion wheel in Villard's album. */
val db81Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 81,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "The Sangjeong Gogeum Yemun, a Goryeo book reported as printed with metal type in 1234",
        originalTitle = "Sangjeong Gogeum Yemun",
        author = "Goryeo court printers",
        year = 1234,
        summary = "Korean records describe a Goryeo printing of the Sangjeong Gogeum Yemun with movable metal type in 1234, a book that is now lost and whose date and method are still argued over.",
        details = "No copy of the 1234 book survives, so the claim rests on the old records and on later accounts of them. Jikji of 1377 is the oldest surviving book printed with metal type, and if the 1234 date is right the Goryeo printers were about 140 years earlier.",
        rare = true,
    ),

    entry(
        databaseId = 81,
        kind = Kind.DEVICE,
        section = "Machines",
        title = "The perpetual-motion wheel in Villard's album of machines",
        originalTitle = "The perpetual-motion wheel in Villard's album of machines",
        author = "Villard de Honnecourt",
        year = 1230,
        summary = "Villard de Honnecourt's album includes a perpetual-motion design, a wheel drawn as if it could keep turning without any outside power.",
        details = "The album's notes discuss the machine beside its other mechanical designs, including a monumental clock tower. No wheel of this kind has ever been shown to run on its own, because it would give out more energy than it takes in.",
        rare = true,
    ),

)
