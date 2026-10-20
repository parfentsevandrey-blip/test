package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 610 to 620: the grinding mill that the Goguryeo monk Docho built in Japan, as the Nihon Shoki records. */
val db143Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 143,
        kind = Kind.DEVICE,
        section = "Japanese",
        title = "The grinding mill that the Goguryeo monk Docho built in Japan in 610, as the Nihon Shoki records",
        originalTitle = "nengai",
        author = "Docho",
        year = 610,
        summary = "The Nihon Shoki records that the Goguryeo monk Docho built a grinding mill, called nengai, in Japan in 610.",
        details = "The 610 entry names Docho among the monks sent from Goguryeo and credits him with learning in the Five Classics and with skill in pigments, paper and ink, besides the mill. Some accounts describe the mill as water-powered, and this record does not rest on that point.",
        rare = true,
    ),

)
