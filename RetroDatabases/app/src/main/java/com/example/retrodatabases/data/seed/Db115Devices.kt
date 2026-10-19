package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 890 to 900: the long warships that King Alfred had built in 896, as the Anglo-Saxon Chronicle describes them. */
val db115Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 115,
        kind = Kind.DEVICE,
        section = "English",
        title = "Alfred's long warships of 896, some with sixty oars, from the Anglo-Saxon Chronicle",
        originalTitle = "Anglo-Saxon Chronicle, annal for 896",
        author = "King Alfred, who had them built",
        year = 896,
        summary = "The Anglo-Saxon Chronicle records that King Alfred had long warships built in 896, some with sixty oars, to fight the Viking raiders.",
        details = "The Chronicle says some of the ships had sixty oars or more, and that they were swifter, steadier and higher than the enemy's ships. The date 896 comes from the Chronicle's own annal, and the ships are known only from that text.",
    ),

)
