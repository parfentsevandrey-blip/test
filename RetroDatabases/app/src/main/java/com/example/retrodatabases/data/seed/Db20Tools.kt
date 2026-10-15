package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1840 to 1850: a reference ellipsoid, a large telescope, a star catalogue, meteorological and laboratory instruments and a stereoscope. */
val db20Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 20,
        kind = Kind.TOOL,
        section = "Geodesy",
        title = "Bessel's reference ellipsoid",
        originalTitle = "Bestimmung der Gestalt des Erdkoerpers",
        author = "Friedrich Wilhelm Bessel",
        year = 1841,
        summary = "Bessel's ellipsoid gives the size and flattening of the Earth as two numbers, and surveyors used it for converting measurements into positions.",
        details = "Bessel published the figure in 1841 from measured arcs of the meridian and the parallel. The ellipsoid served as the reference for many national surveys for more than a century.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.TOOL,
        section = "Telescopes",
        title = "Leviathan telescope of Parsonstown",
        originalTitle = "Leviathan of Parsonstown",
        author = "William Parsons, third Earl of Rosse",
        year = 1845,
        summary = "Lord Rosse's Leviathan was a reflecting telescope with a mirror of about 72 inches, built at Birr Castle in Ireland and completed in 1845.",
        details = "The telescope remained the largest in the world for decades, and Rosse used it to draw the spiral structure of nebulae. Its metal mirror had to be repolished regularly to keep it bright.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.TOOL,
        section = "Astronomical catalogues",
        title = "Herschel's Cape star catalogue",
        originalTitle = "Results of Astronomical Observations made at the Cape of Good Hope",
        author = "John Frederick William Herschel",
        year = 1847,
        summary = "Herschel's reduced observations of southern stars, published in 1847, gave later astronomers a reference catalogue of positions.",
        details = "Herschel made the observations at the Cape of Good Hope in the 1830s and printed the reduced results in 1847. The catalogue became a standard source for southern star positions.",
        rare = true,
    ),

    entry(
        databaseId = 20,
        kind = Kind.TOOL,
        section = "Meteorological instruments",
        title = "Robinson's cup anemometer",
        originalTitle = "Cup anemometer",
        author = "Thomas Romney Robinson",
        year = 1846,
        summary = "Robinson's cup anemometer had four hemispherical cups on horizontal arms, and the rate of spin gave the wind speed.",
        details = "Robinson devised the instrument at Armagh Observatory in 1846, and cup anemometers of this design became common at weather stations.",
        rare = true,
    ),

    entry(
        databaseId = 20,
        kind = Kind.TOOL,
        section = "Laboratory apparatus",
        title = "Kipp's gas generator",
        originalTitle = "Kipp's gas generator",
        author = "Petrus Jacobus Kipp",
        year = 1844,
        summary = "Kipp's apparatus gave a steady supply of gas from an acid and a solid reagent, with the acid topping up automatically from a reservoir.",
        details = "Kipp described the three-chamber apparatus in 1844, and it became standard laboratory equipment for generating hydrogen and carbon dioxide.",
        rare = true,
    ),

    entry(
        databaseId = 20,
        kind = Kind.TOOL,
        section = "Optical instruments",
        title = "Brewster's lenticular stereoscope",
        originalTitle = "Lenticular stereoscope",
        author = "David Brewster",
        year = 1849,
        summary = "Brewster's lenticular stereoscope used a pair of lenses in place of mirrors, giving a compact viewer for stereo pictures.",
        details = "Brewster introduced the lens-based viewer in 1849, and it was made commercially in the years that followed.",
    ),
)
