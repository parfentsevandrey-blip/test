package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.RecordEntity

/**
 * All records of database 42: the 1620s coding catalogue, covering 1620 to 1630, with working code
 * for the guides, examples, projects, flex pieces and cross-code experiments.
 */
val db42Records: List<RecordEntity> =
    db42Languages + db42Systems + db42Devices + db42Tools + db42Practices +
        db42Guides + db42Examples + db42Projects + db42Flex + db42Cross
