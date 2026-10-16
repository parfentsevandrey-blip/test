package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.RecordEntity

/**
 * All records of database 63: the 1410s coding catalogue, covering 1410 to 1420. The database is not a quota database,
 * so it holds fewer records than the usual fifty, with working code for the guides, examples, projects, flex pieces and cross-code experiments.
 */
val db63Records: List<RecordEntity> =
    db63Languages + db63Systems + db63Devices + db63Tools + db63Practices +
        db63Guides + db63Examples + db63Projects + db63Flex + db63Cross
