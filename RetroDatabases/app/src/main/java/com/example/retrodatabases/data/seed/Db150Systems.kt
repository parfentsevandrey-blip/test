package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 540 to 550: the plague of Justinian, which Procopius dates to Pelusium in 541 and to Constantinople in 542. */
val db150Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "The plague of Justinian, which reached Pelusium in Egypt in 541 and Constantinople in 542",
        originalTitle = "The plague of Justinian",
        author = "Procopius of Caesarea, the main account",
        year = 541,
        summary = "A plague that Procopius describes as reaching Pelusium in Egypt in 541 and the capital in 542 killed many people across the eastern Roman world.",
        details = "Procopius dates the first outbreak to 541 and its arrival in Constantinople to the following year, and the disease returned in waves over the decades after. Modern estimates of the death toll vary widely, and Procopius's figures are debated.",
        rare = true,
    ),

)
