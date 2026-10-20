package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 280 to 290: Xun Xu's catalogue of the imperial library, its books arranged in four parts, dated about 280. */
val db176Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 176,
        kind = Kind.TOOL,
        section = "Libraries",
        title = "Xun Xu's catalogue of the imperial library, its books arranged in four parts",
        originalTitle = "Zhongjing xinbu",
        author = "Xun Xu",
        year = 280,
        summary = "Xun Xu's catalogue of the imperial library sorted its books into four parts, an order that later Chinese libraries kept for centuries.",
        details = "The four parts were the classics (Jia), the masters and military writers (Yi), the histories (Bing) and the poetry and rhapsodies with pictures and eulogies (Ding). The catalogue is dated about 280, under the Jin, and its title means the new register of the central library.",
        rare = true,
    ),

)
