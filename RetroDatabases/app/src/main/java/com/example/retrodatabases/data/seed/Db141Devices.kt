package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 630 to 640: Cheomseongdae, the stone sky-watching tower at Gyeongju built in Queen Seondeok's reign, dated 633 in one account. */
val db141Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 141,
        kind = Kind.DEVICE,
        section = "Silla",
        title = "Cheomseongdae, the stone tower for watching the sky at Gyeongju, built in Queen Seondeok's reign",
        originalTitle = "Cheomseongdae",
        author = "The Silla court under Queen Seondeok",
        year = 633,
        summary = "Cheomseongdae is a stone tower at Gyeongju, one of the oldest surviving sky-watching towers in East Asia, built in the reign of Queen Seondeok of Silla.",
        details = "Her reign is given as 632 to 647, and one account dates the building to 633, while another gives 647, so the year is not settled. Most accounts count about 27 layers of stone, and the number of stones is given as 362 in some sources and as 356 or 365 in others. Its use for watching the sky is the usual reading, though the early sources say little about how it was used.",
        rare = true,
    ),

)
