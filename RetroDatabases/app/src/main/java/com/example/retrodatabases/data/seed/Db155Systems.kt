package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 490 to 500: an Ostrogothic kingdom in Italy, a Northern Wei move of the capital, and a bronze coinage of the eastern emperor. */
val db155Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 155,
        kind = Kind.SYSTEM,
        section = "Kingdoms",
        title = "The Ostrogothic kingdom of Italy under Theoderic, founded in 493",
        originalTitle = "Ostrogothic kingdom of Italy",
        author = "Theoderic the Great",
        year = 493,
        summary = "Theoderic set up the Ostrogothic kingdom in Italy in 493 after killing Odoacer, and it held the peninsula until the Byzantine conquest of the 550s.",
        details = "Theoderic had come to Italy with an Ostrogothic army, and the killing of Odoacer, who had deposed the last western emperor in 476, is usually dated to March 493. The kingdom kept the Roman senate and Roman law, with Gothic rulers at the top of the old Roman administration.",
    ),

    entry(
        databaseId = 155,
        kind = Kind.SYSTEM,
        section = "Capitals",
        title = "The Northern Wei court's move to Luoyang, carried out in 494",
        originalTitle = "Qiandu, the move of the capital",
        author = "Emperor Xiaowen of the Northern Wei",
        year = 494,
        summary = "Emperor Xiaowen of the Northern Wei moved his court from Pingcheng to Luoyang in 494, the old capital of the Eastern Han and of Cao Wei.",
        details = "Most accounts date the move to 494, though one account gives 493, and the court's rule on the Chinese language belongs to the same reforms. Accounts differ on whether that rule was an outright ban or a discouragement.",
    ),

    entry(
        databaseId = 155,
        kind = Kind.SYSTEM,
        section = "Money",
        title = "Anastasius's monetary reform and the bronze follis, introduced in 498",
        originalTitle = "Follis",
        author = "Anastasius I",
        year = 498,
        summary = "Anastasius I introduced a bronze coin called the follis in 498, a small coinage marked with the letter M, which stood for forty nummi.",
        details = "Several accounts date the introduction of the follis to 498, and numismatists still discuss the exact terms of the reform. The M on the coin reads as forty, the value of the coin in nummi.",
    ),

)
