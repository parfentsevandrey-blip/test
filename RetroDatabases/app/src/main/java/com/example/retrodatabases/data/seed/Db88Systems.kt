package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1160 to 1170: the Clarendon assizes and constitutions, the move of English scholars to Oxford, the Lombard League and the Longxing peace. */
val db88Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 88,
        kind = Kind.SYSTEM,
        section = "English law",
        title = "The Assize of Clarendon, sworn juries of presentment for each county",
        originalTitle = "Assisa de Clarendon",
        author = "Henry II of England",
        year = 1166,
        summary = "The Assize of Clarendon of 1166 set up sworn juries that reported accused people to royal justices in every county.",
        details = "It named twelve men from each hundred and four from each township as the jurors who made these reports, and it set out their duties in writing. The date 1166 is the usual one for the assize.",
    ),

    entry(
        databaseId = 88,
        kind = Kind.SYSTEM,
        section = "English law",
        title = "The Constitutions of Clarendon, sixteen customs on the church and the courts",
        originalTitle = "Constitutiones de Clarendon",
        author = "Henry II and the English bishops",
        year = 1164,
        summary = "The Constitutions of Clarendon, issued in 1164, set out sixteen customs that limited the church's courts and clergy, and they led to Thomas Becket's exile.",
        details = "Becket first gave his assent to the customs and then withdrew it, and the quarrel that followed ended with his murder in 1170. The text survives in several copies, and accounts differ on the wording of some clauses.",
    ),

    entry(
        databaseId = 88,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "Henry II's ban of 1167 that sent English scholars from Paris to Oxford",
        originalTitle = "Migration from Paris to Oxford",
        author = "Henry II of England",
        year = 1167,
        summary = "In 1167 Henry II forbade English clerks to study at Paris, and many of them went to Oxford, where a university began to grow.",
        details = "The traditional account links the ban to a quarrel between English and French students in Paris, but accounts differ on the cause and on how many scholars left. The migration is one standard explanation for the early growth of Oxford as a place of teaching.",
    ),

    entry(
        databaseId = 88,
        kind = Kind.SYSTEM,
        section = "Italian cities",
        title = "The Lombard League of 1167, an alliance of cities against Frederick Barbarossa",
        originalTitle = "Lega Lombarda",
        author = "The cities of Lombardy",
        year = 1167,
        summary = "The Lombard League of 1167 was an alliance of northern Italian cities that joined forces to resist the imperial claims of Frederick Barbarossa.",
        details = "The cities swore to defend one another, and the league met Frederick in battle at Legnano in 1176. Its membership changed over the years, and accounts differ on which cities made up the first league.",
    ),

    entry(
        databaseId = 88,
        kind = Kind.SYSTEM,
        section = "Diplomacy",
        title = "The Longxing peace between the Song and Jin courts, a settlement of 1164",
        originalTitle = "Longxing heyi",
        author = "The Song and Jin courts",
        year = 1164,
        summary = "The Longxing peace ended the fighting between the Song and Jin courts and fixed the terms of a border peace in northern China.",
        details = "It followed a failed Song northern campaign of 1164. Accounts differ on the year of the settlement, 1164 or 1165, and the record here follows the earlier date.",
        rare = true,
    ),

)
