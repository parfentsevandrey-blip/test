package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.RecordEntity

/**
 * All records of database 1: languages, systems, devices, tools and practices of the 1980s,
 * as well as guides, examples, projects, flex projects and cross-code experiments.
 * The display order is set by the screen (kind → section → year), not by the order in these lists.
 */
val db1Records: List<RecordEntity> =
    db1Languages + db1Systems + db1Devices + db1Tools + db1Practices +
        db1Guides + db1Examples + db1Projects + db1Flex + db1Cross + db1Additions
