package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 210 to 220: Caracalla's Constitutio Antoniniana, the grant of citizenship dated to 212. */
val db183Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 183,
        kind = Kind.SYSTEM,
        section = "Citizenship",
        title = "Caracalla's Constitutio Antoniniana, citizenship for most free people of the empire",
        originalTitle = "Constitutio Antoniniana",
        author = "Caracalla (Marcus Aurelius Antoninus)",
        year = 212,
        summary = "Caracalla's edict of 212 gave Roman citizenship to most free inhabitants of the empire, and its text survives in a Greek papyrus from Egypt.",
        details = "Most scholars date the edict to 212, perhaps to July, though one proposal puts it in 214. The text survives in the Giessen papyrus, acquired in Egypt in 1902 and known as P.Giss. 40, while Cassius Dio ties the grant to the taxes that only citizens paid and accounts differ on whether revenue or policy was the aim.",
    ),

)
