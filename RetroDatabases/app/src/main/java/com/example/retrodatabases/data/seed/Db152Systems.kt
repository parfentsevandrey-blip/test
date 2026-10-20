package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 520 to 530: the Second Council of Orange of 529 and the first Code of Justinian, published in 529. */
val db152Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 152,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Second Council of Orange of 529, whose twenty-five canons ruled on grace and free will against semi-Pelagian teaching",
        originalTitle = "Concilium Arausicanum II",
        author = "Caesarius of Arles and the bishops present",
        year = 529,
        summary = "The Second Council of Orange issued twenty-five canons that rejected semi-Pelagian teaching and taught that the beginning of faith comes from grace.",
        details = "Caesarius, bishop of Arles, led the council at Orange in Gaul in 529, and its canons drew on Augustine's teaching. Pope Boniface II confirmed the canons in 531, and the council is still cited in Catholic teaching on grace.",
        rare = true,
    ),

    entry(
        databaseId = 152,
        kind = Kind.SYSTEM,
        section = "Byzantine law",
        title = "The first Code of Justinian, a collection of imperial laws published in 529 by a commission led by Tribonian",
        originalTitle = "Codex Iustinianus",
        author = "Justinian and the commission led by Tribonian",
        year = 529,
        summary = "The first Code of Justinian gathered the imperial constitutions then in force into a single collection in 529.",
        details = "Justinian set up the commission in 528 under Tribonian, and the first edition was published in 529. Justinian replaced it with a revised second edition in 534.",
    ),

)
