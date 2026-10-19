package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 860 to 870: Charles the Bald's Edict of Pitres of 864, Pope Nicholas I's answers to the Bulgarians of 866, and Basil I's sole rule of 867. */
val db118Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 118,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Edict of Pitres, Charles the Bald's capitulary on coinage and bridge defences",
        originalTitle = "Edictum Pistense",
        author = "Charles the Bald",
        year = 864,
        summary = "Charles the Bald's Edict of Pitres of June 864 confined minting to the palace and a list of named sites, banned underweight coins and called for fortified bridges against Viking raids.",
        details = "The edict was issued at Pitres in late June 864, and it set penalties for counterfeiting and put horse-owners on call for a mounted reserve. Historians doubt how far the plan for fortified bridges was carried out, and Simon MacLean challenges the standard view that castles grew out of these anti-Viking defences.",
        rare = true,
    ),

    entry(
        databaseId = 118,
        kind = Kind.SYSTEM,
        section = "Canon law",
        title = "Pope Nicholas I's answers to the Bulgarians, a canon-law reply to a new Christian kingdom",
        originalTitle = "Responsa ad consulta Bulgarorum",
        author = "Pope Nicholas I",
        year = 866,
        summary = "Pope Nicholas I's answers of 866 to the questions of Boris I of Bulgaria gave the newly baptised kingdom rules on church practice and daily life.",
        details = "Boris put a list of questions to Rome on matters that included marriage and fasting, and the pope's replies are dated to 866, the year usually given for them. Their subject is church practice in a kingdom that had only just accepted baptism.",
        rare = true,
    ),

    entry(
        databaseId = 118,
        kind = Kind.SYSTEM,
        section = "Byzantium",
        title = "Basil I's sole rule, which began the Macedonian dynasty of Byzantium",
        originalTitle = "Basileios I",
        author = "Basil I",
        year = 867,
        summary = "Basil I became sole emperor of Byzantium in September 867 after the murder of Michael III, and the Macedonian dynasty he founded ruled until 1056.",
        details = "Basil had been co-emperor since 866, and the main accounts of the murder were written under his dynasty, so they should be read with that in mind. The line ended with the death of Theodora in 1056.",
    ),

)
