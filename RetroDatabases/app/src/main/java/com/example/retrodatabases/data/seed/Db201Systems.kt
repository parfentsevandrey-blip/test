package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 30 to 40: the Aqua Claudia, begun under Caligula, and the bridge of ships across the bay between Baiae and Puteoli. */
val db201Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 201,
        kind = Kind.SYSTEM,
        section = "Water",
        title = "The Aqua Claudia, a Roman aqueduct begun by Caligula in 38 and finished by Claudius",
        originalTitle = "Aqua Claudia",
        author = "Caligula, then Claudius",
        year = 38,
        summary = "The Aqua Claudia was a Roman aqueduct that carried water to Rome from springs in the hills to the east, begun by Caligula in 38 and finished by Claudius in 52.",
        details = "Caligula started two aqueducts in the same year, the Aqua Claudia and the Anio Novus, and Claudius completed both. Older accounts give 36 for the start and 50 for the finish, so the dates differ from source to source, and 38 and 52 are the usual dates.",
    ),

    entry(
        databaseId = 201,
        kind = Kind.SYSTEM,
        section = "Engineering",
        title = "Caligula's bridge of boats across the bay between Baiae and Puteoli",
        originalTitle = "Caligula's bridge of boats",
        author = "Caligula, emperor of Rome",
        year = 39,
        summary = "Caligula had a line of ships moored across the bay between Baiae and Puteoli and joined into a bridge, a spectacle that Roman writers describe.",
        details = "Suetonius gives the crossing as about 3,600 paces, and modern estimates put it near three miles, so the figures agree only roughly. The usual date is 39, although some accounts give 37, and ancient writers compare the bridge with Xerxes's bridge over the Hellespont.",
    ),

)
