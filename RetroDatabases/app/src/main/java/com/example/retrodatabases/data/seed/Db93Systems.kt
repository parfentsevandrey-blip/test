package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1110 to 1120: the Jin state, a cathedral school at Laon, the pact of 1111, two religious orders of Jerusalem and the papal bull that protected the Hospitallers. */
val db93Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 93,
        kind = Kind.SYSTEM,
        section = "States",
        title = "The Jin state of the Jurchen, proclaimed in 1115 by Aguda",
        originalTitle = "Jin",
        author = "Aguda, the first Jin emperor",
        year = 1115,
        summary = "The Jurchen leader Aguda proclaimed the Jin state in 1115, and it ruled northern China until its fall in 1234.",
        details = "The Jin state rested on Jurchen military organization and took much of northern China first from the Liao and then from the Song. Its court ordered the large Jurchen script in 1119, so the state's government and its writing system were both set up in the same decade.",
    ),

    entry(
        databaseId = 93,
        kind = Kind.SYSTEM,
        section = "Schools",
        title = "The cathedral school at Laon, taught by Anselm of Laon in the 1110s",
        originalTitle = "The cathedral school at Laon",
        author = "Anselm of Laon",
        year = 1113,
        summary = "The cathedral school at Laon, taught by Anselm of Laon, drew students in the 1110s, and Peter Abelard came there about 1113.",
        details = "Abelard's own account of his stay is the main source, and he found the teaching disappointing and left after a short time. Anselm died in 1117, and the school stood among the leading centres for the study of the Bible in the early twelfth century.",
    ),

    entry(
        databaseId = 93,
        kind = Kind.SYSTEM,
        section = "Church and realm",
        title = "The pact of 1111 between Henry V and Pope Paschal II, which collapsed",
        originalTitle = "Pactum",
        author = "Henry V and Pope Paschal II",
        year = 1111,
        summary = "In February 1111 Henry V and Pope Paschal II agreed a pact to end royal investiture of bishops, but the plan collapsed at the coronation and the king seized the pope.",
        details = "The pact proposed that bishops give up the royal rights attached to their sees, in return for an end to lay investiture, and accounts differ on its exact terms and on who first broke the deal. The quarrel over investiture was settled only by the Concordat of Worms in 1122.",
        rare = true,
    ),

    entry(
        databaseId = 93,
        kind = Kind.SYSTEM,
        section = "Orders",
        title = "Pope Paschal II's bull Pie postulatio voluntatis for the Hospitallers",
        originalTitle = "Pie postulatio voluntatis",
        author = "Pope Paschal II",
        year = 1113,
        summary = "The papal bull Pie postulatio voluntatis, issued by Paschal II in 1113, placed the Hospital of Saint John at Jerusalem under papal protection.",
        details = "The bull is commonly dated to February 1113 and is often taken as an early papal recognition of the Hospitallers as a religious body. Later bulls extended the order's privileges, so the 1113 bull is only one stage in its formal growth.",
    ),

    entry(
        databaseId = 93,
        kind = Kind.SYSTEM,
        section = "Orders",
        title = "The Poor Fellow-Soldiers of Christ, known as the Templars, founded about 1119",
        originalTitle = "Pauperes commilitones Christi Templique Salomonis",
        author = "Hugh of Payns and his companions",
        year = 1119,
        summary = "A group of knights under Hugh of Payns began to guard the pilgrim roads of Jerusalem about 1119, and the group became the order known as the Templars.",
        details = "Accounts differ on the founding date, with sources giving 1118, 1119 or 1120, and the rule was approved at the Council of Troyes in 1129. The name comes from the Temple of Solomon, the site in Jerusalem where the knights were traditionally housed.",
    ),

)
