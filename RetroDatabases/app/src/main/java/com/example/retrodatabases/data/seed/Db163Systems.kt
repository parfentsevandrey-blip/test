package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 410 to 418: the Visigothic kingdom of Toulouse under its treaty, and the Carthage council that condemned Pelagius. */
val db163Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 163,
        kind = Kind.SYSTEM,
        section = "Visigoths",
        title = "The Visigothic kingdom of Toulouse, set up under the treaty of 418",
        originalTitle = "Regnum Tolosanum",
        author = "Wallia and the Visigothic kings",
        year = 418,
        summary = "A treaty with the western Roman court in 418 settled the Visigoths in Aquitaine, and their kingdom with its centre at Toulouse lasted until 507.",
        details = "The treaty is usually dated to 418, in the reign of the Visigothic king Wallia, and accounts differ between 418 and 419 for the year the Toulouse kingdom took its full shape. The Visigoths lost Toulouse to the Franks under Clovis in the battle of Vouille in 507.",
        rare = true,
    ),

    entry(
        databaseId = 163,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Council of Carthage of 418 against Pelagius",
        originalTitle = "Concilium Carthaginense",
        author = "The African bishops under Aurelius of Carthage",
        year = 418,
        summary = "A council of African bishops at Carthage in 418 condemned the teaching of Pelagius on grace and on original sin.",
        details = "The council held that people need grace to do good and that infants inherit sin, against the view of Pelagius that human will alone could be enough. The council is usually dated to 418, and the same argument over grace returned at the Council of Orange in 529.",
        rare = true,
    ),

)
