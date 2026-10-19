package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 710 to 720: Tariq's landing in Spain and Muhammad ibn Qasim's attack on Debal, both in 711, and Liutprand's first laws for the Lombards in 713. */
val db133Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 133,
        kind = Kind.SYSTEM,
        section = "Umayyad",
        title = "Tariq ibn Ziyad's landing at Gibraltar in 711 and the start of the Arab conquest of Visigothic Spain",
        originalTitle = "Jabal Tariq",
        author = "Tariq ibn Ziyad",
        year = 711,
        summary = "Tariq ibn Ziyad landed at Gibraltar in 711 with an army made largely of Berbers, and his victory over the Visigothic king Roderic at the Guadalete began the Arab conquest of Spain.",
        details = "The rock at the landing place is named after him, Jabal Tariq, which is the origin of the name Gibraltar. Accounts give different figures for the size of his army, but 711 is the usual year both for the landing and for the battle at the Guadalete that followed.",
    ),

    entry(
        databaseId = 133,
        kind = Kind.SYSTEM,
        section = "Umayyad",
        title = "Muhammad ibn Qasim's conquest of Sind, begun by the capture of Debal in 711",
        originalTitle = "Conquest of Sind",
        author = "Muhammad ibn Qasim",
        year = 711,
        summary = "Muhammad ibn Qasim, an Umayyad commander, took the port of Debal in 711 and began the conquest of Sind, the region of the lower Indus.",
        details = "The main narrative is the Chachnama, a Persian history written centuries later, so its account of the siege of Debal and of the campaigns after it is not all contemporary. Accounts differ on whether the conquest was complete in 712 or 713, and the record uses 711, the year of the attack on Debal.",
        rare = true,
    ),

    entry(
        databaseId = 133,
        kind = Kind.SYSTEM,
        section = "Lombard",
        title = "The first laws of Liutprand, king of the Lombards, issued in 713, the first year of his legislation",
        originalTitle = "Leges Liutprandi",
        author = "Liutprand, king of the Lombards",
        year = 713,
        summary = "Liutprand, who became king of the Lombards in 712, issued his first laws in 713, and the Lombard law codes had begun with the Edictus Rothari of 643.",
        details = "Liutprand reigned from 712 to 744, and the record dates his legislation from 713, the first year of the surviving laws. He is not to be confused with Liutprand of Cremona, the tenth-century envoy.",
        rare = true,
    ),

)
