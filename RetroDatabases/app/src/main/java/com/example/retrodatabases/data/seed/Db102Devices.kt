package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1020 to 1030: a brass astrolabe made at Cordoba by Muhammad ibn al-Saffar and dated 417 AH. */
val db102Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 102,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Ibn al-Saffar's brass astrolabe of Cordoba, dated 417 AH",
        originalTitle = "Ibn al-Saffar's brass astrolabe of Cordoba, dated 417 AH",
        author = "Muhammad ibn al-Saffar",
        year = 1026,
        summary = "A brass astrolabe made at Cordoba by Muhammad ibn al-Saffar is dated 417 AH, which falls in 1026 or 1027 CE, and it is held by National Museums Scotland in Edinburgh.",
        details = "The signature and the date place the instrument among the older surviving examples from Europe. It turned 1000 years old in 2026.",
        rare = true,
    ),

)
