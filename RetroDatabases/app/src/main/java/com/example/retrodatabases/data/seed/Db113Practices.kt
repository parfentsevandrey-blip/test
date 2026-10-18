package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 910 to 920: the charter of 910 that put the monastery of Cluny beyond local control. */
val db113Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 113,
        kind = Kind.PRACTICE,
        section = "France",
        title = "The charter of Cluny, which freed the abbey from lay and episcopal control",
        originalTitle = "Cluny foundation charter, in Latin",
        author = "William I of Aquitaine",
        year = 910,
        summary = "William I of Aquitaine founded Cluny Abbey in 910 by a charter that placed it under the protection of Rome and let the monks choose their own abbot.",
        details = "The charter, dated 11 September 910, put the abbey beyond the control of local lay lords and bishops, and its monks elected their own abbot. Its freedoms became a model for later monastic reform, which is why the charter is cited in histories of the Cluniac movement.",
    ),

)
