package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 249: the seizure of power at the Gaoping Tombs, by which Sima Yi put the Wei state under his family. */
val db180Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 180,
        kind = Kind.SYSTEM,
        section = "Wei",
        title = "Sima Yi's seizure of power at the Gaoping Tombs in 249",
        originalTitle = "Gaoping ling zhi bian",
        author = "Sima Yi",
        year = 249,
        summary = "In 249 Sima Yi seized the Wei capital while the regent Cao Shuang was away with the young emperor, and the Sima family then held power in the Wei state.",
        details = "Cao Shuang took the emperor Cao Fang out of the capital to visit the imperial tombs, and Sima Yi took control of the court in his absence. Cao Shuang and his brothers were executed later that year, and the Sima family's hold on Wei led in time to the Jin dynasty.",
    ),

)
