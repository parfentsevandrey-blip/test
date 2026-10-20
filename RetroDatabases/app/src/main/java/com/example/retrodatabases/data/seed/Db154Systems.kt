package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Law and church rules of 500 to 510: Alaric II's Breviary of Roman law and the Council of Agde, both of 506. */
val db154Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 154,
        kind = Kind.SYSTEM,
        section = "Visigothic",
        title = "Alaric II's Breviary of Roman law, issued in 506 for the Roman subjects of the Visigothic kingdom",
        originalTitle = "Lex Romana Visigothorum",
        author = "Alaric II and his counsellors",
        year = 506,
        summary = "Alaric II's digest of Roman law, issued in 506, gave the Roman subjects of the Visigoths the laws their courts used.",
        details = "It gathered extracts from the Theodosian Code, the Institutes of Gaius and the Sentences of Paul, each with a short Latin interpretation. Many manuscripts of it survive, which shows how widely it was copied.",
        rare = true,
    ),

    entry(
        databaseId = 154,
        kind = Kind.SYSTEM,
        section = "Visigothic",
        title = "The Council of Agde of 506, a Gallic synod under the Visigothic king Alaric II that issued canons for the church",
        originalTitle = "Concilium Agathense",
        author = "Bishops of Gaul under Alaric II",
        year = 506,
        summary = "A synod of Gallic bishops at Agde in 506, held under Visigothic rule, issued canons on the clergy, the monks and church property.",
        details = "The bishops met with the king's permission, and their canons are an early record of church law in southern Gaul. The Breviary was issued in the same year, so 506 is a busy year for written rules under Alaric II.",
        rare = true,
    ),

)
