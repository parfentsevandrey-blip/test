package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 280 to 290: Chen Shou's Records of the Three Kingdoms, placed at 280 because its completion year is not fixed. */
val db176Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 176,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Chen Shou's Records of the Three Kingdoms, a history of Wei, Shu and Wu",
        originalTitle = "Sanguozhi",
        author = "Chen Shou",
        year = 280,
        summary = "Chen Shou's Records of the Three Kingdoms is a Chinese history of the states of Wei, Shu and Wu, written under the Jin in the 280s.",
        details = "The work fills 65 volumes and includes the Wei account of the Wa people of Japan. Its completion falls in the 280s, close to the Jin conquest of Wu, but the sources do not fix one year, so 280 is the placed year, the year of that anchor, and not a date of completion.",
    ),

)
