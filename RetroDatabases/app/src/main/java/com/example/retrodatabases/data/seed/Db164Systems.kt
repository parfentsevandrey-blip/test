package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 400 to 410: the synod of Mar Isaac for the Christians of Persia, Honorius's move to Ravenna and the synod of the Oak that deposed Chrysostom. */
val db164Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 164,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The synod of Mar Isaac at Seleucia-Ctesiphon, which gave the Christians of Persia one church structure",
        originalTitle = "Synod of Mar Ishaq",
        author = "Catholicos Mar Isaac and the bishops of Persia",
        year = 410,
        summary = "In 410 the Persian king Yazdegerd I summoned a synod of Christian bishops at Seleucia-Ctesiphon, which gave the Christians of Persia one church structure.",
        details = "The assembly is known as the synod of Mar Isaac, and its acts survive in the Synodicon Orientale, a later collection of Syriac church records. The structure set up there grew into the Church of the East, which later spread across Asia.",
        rare = true,
    ),

    entry(
        databaseId = 164,
        kind = Kind.SYSTEM,
        section = "Empire",
        title = "Honorius moves the western court from Milan to Ravenna",
        originalTitle = "Ravenna",
        author = "The emperor Honorius",
        year = 402,
        summary = "In 402 the western emperor Honorius moved his court from Milan to Ravenna, a city of marshes that remained the western seat until 476.",
        details = "Accounts give 402 or 404 for the move, since the settling of the court in the city is dated differently in different sources. The marshes around Ravenna made the city hard to reach by land, which is one reason given for the choice while Gothic armies threatened northern Italy.",
    ),

    entry(
        databaseId = 164,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Synod of the Oak at Chalcedon, which deposed John Chrysostom",
        originalTitle = "Synodus ad Quercum",
        author = "Theophilus of Alexandria and his allies",
        year = 403,
        summary = "A synod held at the Oak, a place near Chalcedon, deposed the patriarch John Chrysostom of Constantinople in 403, and his supporters rejected the verdict.",
        details = "Theophilus of Alexandria presided over the synod, which charged Chrysostom with misconduct and led to his exile from the capital. The dispute over the deposition ran for years between Constantinople, Alexandria and Rome.",
    ),

)
