package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 250 to 260: Diophantus's Arithmetika and a Latin treatise by Cyprian on the unity of the church. */
val db179Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 179,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Diophantus's Arithmetika, problems solved with a symbol for the unknown",
        originalTitle = "Arithmetika",
        author = "Diophantus of Alexandria",
        year = 250,
        summary = "Diophantus's Arithmetika is a Greek book of problems in which a symbol stands for the unknown number, and it is dated to about 250.",
        details = "Accounts differ on his date: one estimate puts him about 240, and quotations from later writers bracket him between about 150 BC and AD 350, so 250 is the year most often given. Of the thirteen books the work once had, six survive in Greek and the rest are known from an Arabic translation or are lost.",
    ),

    entry(
        databaseId = 179,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Cyprian's On the Unity of the Catholic Church, a Latin treatise of 251",
        originalTitle = "De catholicae ecclesiae unitate",
        author = "Cyprian of Carthage",
        year = 251,
        summary = "Cyprian wrote his Latin treatise on the unity of the Catholic Church in 251, arguing that the church stays one body under its bishops.",
        details = "The treatise was written at Carthage in the years of the schism that Novatian caused at Rome, and it treats a breach of unity as a breach with the church itself. The date 251 is the one usually given for the text.",
        rare = true,
    ),

)
