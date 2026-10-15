package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1910 to 1920: a fire-control table, census counting, teleprinter and switching services, and an early broadcast. */
val db13Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 13,
        kind = Kind.SYSTEM,
        section = "Fire control",
        title = "Dreyer fire-control table",
        originalTitle = "Dreyer Table, the Royal Navy gunnery calculator (Mark III, 1912)",
        author = "Frederic Charles Dreyer",
        year = 1912,
        summary = "A mechanical fire-control table of linked scales and plotters, which estimated how quickly the range to a target was changing so that battleship guns could be laid.",
        details = "The Mark III table entered service in 1912, and it carried a gyroscope. The Admiralty chose Dreyer's design over Arthur Pollen's Argo clock in 1913. Nearly every ship at Jutland in 1916 carried one, and wartime versions allowed for the firing ship's turns, wind and drift. The monitor Roberts kept one in service until 1965.",
        rare = true,
    ),

    entry(
        databaseId = 13,
        kind = Kind.SYSTEM,
        section = "Census",
        title = "Census Bureau tabulators of 1910",
        originalTitle = "Tabulation of the 1910 census of the United States",
        author = "United States Census Bureau",
        year = 1910,
        summary = "The 1910 census was counted on punched cards by machines that the Census Bureau had built itself, to a design patented by its own engineer, James Powers.",
        details = "The Bureau had paid high rents for Hollerith machines for the count of 1900, and it turned to machines of its own for 1910. The machines read the holes in the cards and added the totals as the cards passed through. The company that later sold Powers machines has a separate record, so this one is about the census count.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.SYSTEM,
        section = "Telegraphy",
        title = "The first commercial teleprinter service",
        originalTitle = "Postal Telegraph teleprinter circuit, Boston to New York (1910)",
        author = "Morkrum Company and Postal Telegraph Company",
        year = 1910,
        summary = "Messages typed at one end of a wire were printed at the other, on the earliest commercial teletypewriter service, which ran between Boston and New York in 1910.",
        details = "The service used the Blue Code version of the Morkrum Printing Telegraph. Patent disputes over the start-stop method led Morkrum and Kleinschmidt to join in a merger, which accounts date to 1924 or 1925. The combined company was renamed the Teletype Corporation in December 1928.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.SYSTEM,
        section = "Switching",
        title = "The crossbar selector of 1915",
        originalTitle = "Crossbar selector, Western Electric (Reynolds, drawn 1913, patented 1915)",
        author = "J. N. Reynolds, Western Electric",
        year = 1915,
        summary = "A switch built from crossing bars, with a contact at each crossing, so that a telephone call can be routed through a fixed grid of contacts.",
        details = "The Telecommunications Heritage Group dates the first crossbar design to 1913, when Reynolds of Western Electric drew it. The design was patented in 1915 and never reached commercial use. The first large crossbar exchange dates from the mid-1920s, according to a 1953 technical paper.",
        rare = true,
    ),

    entry(
        databaseId = 13,
        kind = Kind.SYSTEM,
        section = "Radio",
        title = "Opera broadcast from the Metropolitan Opera, 1910",
        originalTitle = "Experimental radio transmission of an opera from the Metropolitan Opera House, New York (De Forest, 1910)",
        author = "Lee de Forest",
        year = 1910,
        summary = "Lee de Forest's experimental transmitter sent a live performance from the Metropolitan Opera House in New York, one of the earliest public radio broadcasts.",
        details = "Accounts give 12 or 13 January 1910 for the broadcast, and the lists of operas and singers differ between sources. Enrico Caruso sang in performances named in several accounts. Reception was confined to De Forest's laboratory, harbor ships, some hotels and press stations.",
    ),
)
