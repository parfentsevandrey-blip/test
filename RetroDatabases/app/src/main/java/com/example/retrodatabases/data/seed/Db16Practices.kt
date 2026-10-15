package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1880 to 1890: time zones, census counting, measurement, electrical units and cipher rules. */
val db16Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 16,
        kind = Kind.PRACTICE,
        section = "Time",
        title = "Standard time zones and the 1884 meridian",
        originalTitle = "International Meridian Conference",
        author = "International Meridian Conference, Washington",
        year = 1884,
        summary = "A system of hour zones measured from one prime meridian, which an international conference chose at Greenwich in October 1884.",
        details = "American railways adopted standard time on 18 November 1883, the day of two noons. The Washington conference of October 1884 voted for a prime meridian at Greenwich by a majority, with France abstaining. Zones measured from one meridian let timetables and telegraph time signals work across a region.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.PRACTICE,
        section = "Census tabulation",
        title = "The 1880 census counted by hand",
        originalTitle = "Tenth Census of the United States",
        author = "United States Census Office",
        year = 1880,
        summary = "The census of 1880 was tallied by clerks with tally marks, and the work ran on for years, which made the case for a machine tabulator.",
        details = "The Census Office counted the 1880 returns by hand, and the published tables were not complete until about 1888. The delay made the case for machinery, which led to a punched-card tabulator for the 1890 census. This record covers the 1880 count and its hand tally only.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.PRACTICE,
        section = "Measurement",
        title = "Galton's anthropometric laboratory",
        originalTitle = "Anthropometric Laboratory",
        author = "Francis Galton",
        year = 1884,
        summary = "A laboratory where visitors paid a small fee to have their height, weight and senses measured and recorded on cards.",
        details = "Galton set up the laboratory at the International Health Exhibition in London in 1884, and visitors had measurements taken for a small fee. The cards gave Galton a large body of data, which he used in his 1888 work on co-relation. The record covers the measuring practice and not the statistics.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.PRACTICE,
        section = "Units",
        title = "The electrical units named at Paris in 1881",
        originalTitle = "Congres international des electriciens, Paris (1881)",
        author = "International Electrical Congress",
        year = 1881,
        summary = "A meeting that gave the practical electrical units the names ohm, volt and ampere, and tied their sizes to the centimetre gram second system.",
        details = "The International Electrical Congress meeting in Paris in 1881 adopted names for the practical electrical units, among them the ohm, the volt and the ampere. Their sizes were defined against the centimetre gram second system, and later congresses refined the definitions. A London conference on the same units in 1908 belongs to the database for 1900 to 1910.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.PRACTICE,
        section = "Cryptography",
        title = "Kerckhoffs's six rules for military ciphers",
        originalTitle = "La cryptographie militaire",
        author = "Auguste Kerckhoffs",
        year = 1883,
        summary = "Six requirements for a military cipher, the best known of which is that it must stay secure even when the enemy knows everything but the key.",
        details = "Kerckhoffs gave the six requirements in his 1883 work La cryptographie militaire. The principle that the system may be public while the key stays secret is still the standard test for a cipher. Shannon stated the same idea in 1949 in a form that became a maxim.",
    ),
)
