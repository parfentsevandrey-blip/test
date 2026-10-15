package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1800 to 1810: surveys of coasts, land and India, a military academy and a monitorial school. */
val db24Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 24,
        kind = Kind.SYSTEM,
        section = "Coastal surveying",
        title = "The US Coast Survey, authorised in 1807",
        originalTitle = "Act for the survey of the coasts of the United States (1807)",
        author = "Congress and President Thomas Jefferson",
        year = 1807,
        summary = "A federal survey of the coasts, authorised in 1807, planned a triangulation network from which harbour and shoreline charts would be drawn.",
        details = "Accounts differ on whether the act is dated 9 or 10 February 1807, since Congress passed it and the President signed it on consecutive days. Ferdinand Hassler, a Swiss-born scientist, became the first superintendent, but field work began only in 1816 and 1817, and the survey was interrupted in the years after 1818.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.SYSTEM,
        section = "Triangulation",
        title = "The Great Trigonometrical Survey of India, begun in 1802",
        originalTitle = "Great Trigonometrical Survey, first base line measured near Madras (1802)",
        author = "William Lambton",
        year = 1802,
        summary = "William Lambton began a chain of triangles across India in 1802 from a base line measured near Madras, and the survey ran for most of the nineteenth century.",
        details = "Lambton's survey began in 1802 with a measured base line near Madras and carried a chain of triangles north across the peninsula. George Everest succeeded Lambton as superintendent in 1823, and the work continued for decades after that.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.SYSTEM,
        section = "Land survey",
        title = "The French parcel cadastre of 1807",
        originalTitle = "Law on the land tax, establishing the cadastral survey (1807)",
        author = "The Napoleonic government of France",
        year = 1807,
        summary = "A law of 1807 set out a parcel-by-parcel survey of French land, so that each plot had a map entry and a register that the tax office could search.",
        details = "The 1807 law ordered a cadastral survey of land parcels, and the work took decades and was uneven from one department to another. Its map sheets and registers are an early national record kept by plot rather than by owner.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.SYSTEM,
        section = "Monitorial teaching",
        title = "Lancaster's monitorial schools, taught by pupil monitors",
        originalTitle = "Improvements in Education, as it respects the industrious classes of the community (1803)",
        author = "Joseph Lancaster",
        year = 1803,
        summary = "Lancaster taught large classes through older pupils acting as monitors, who passed on lessons to small groups under one master's direction.",
        details = "Lancaster's Improvements in Education, printed in 1803, describes his method of monitors, in which pupils taught small groups while a master supervised the whole room. The method spread to several countries in the following decades, and his school at Borough Road in London was its model.",
        rare = true,
    ),

    entry(
        databaseId = 24,
        kind = Kind.SYSTEM,
        section = "Engineering education",
        title = "The United States Military Academy at West Point, founded in 1802",
        originalTitle = "Act establishing the Corps of Engineers and the Military Academy (1802)",
        author = "Congress of the United States",
        year = 1802,
        summary = "Congress set up a military academy at West Point in 1802, and its engineering course trained officers in surveying and mapping.",
        details = "The act of 16 March 1802 created the Corps of Engineers and the academy, which opened that year with a small class. Its course stressed mathematics, drawing and surveying, and graduates worked on fortifications, roads and surveys in the decades that followed.",
    ),
)
