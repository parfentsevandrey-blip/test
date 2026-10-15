package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1610 to 1620: Galileo's moons as a clock at sea, a sun projected for sunspots, a meridian measured by triangles, base ten logarithms and a cask gauged in slices. */
val db43Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 43,
        kind = Kind.PRACTICE,
        section = "Longitude",
        title = "Galileo's eclipses of Jupiter's moons as a clock at sea",
        originalTitle = "Galileo's eclipses of Jupiter's moons as a clock at sea",
        author = "Galileo Galilei",
        year = 1612,
        summary = "Galileo proposed that the eclipses of Jupiter's moons, timed against tables, could give a ship's longitude, since each eclipse marked a moment that the same observer could read anywhere on Earth.",
        details = "His notes on the eclipses are documented from 1612, and the sources date his offer of the method to Spain variously to 1615, 1616 and 1617, so the date of the proposal is disputed. The method needed tables of the moons and a steady view of Jupiter from a moving ship, and it was not adopted at sea in his lifetime.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.PRACTICE,
        section = "Logarithms",
        title = "Napier and Briggs agree on base ten logarithms",
        originalTitle = "Napier and Briggs agree on base ten logarithms",
        author = "John Napier and Henry Briggs",
        year = 1615,
        summary = "In 1615 Briggs visited Napier in Scotland, and the two agreed that the logarithm of 1 should be 0 and that the logarithm of 10 should be 1, which made the tables easier to use.",
        details = "The meeting was held in Edinburgh in 1615, and it led to the base ten tables that Briggs later printed. Napier died in 1617, and the sources give different accounts of the details of the meeting.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.PRACTICE,
        section = "Astronomy",
        title = "Galileo's sun projected onto paper for sunspots",
        originalTitle = "Galileo's sun projected onto paper for sunspots",
        author = "Galileo Galilei",
        year = 1612,
        summary = "Galileo projected the image of the Sun through a telescope onto a card, so that sunspots could be traced and timed without looking straight at the Sun.",
        details = "The practice was used in 1612, and Galileo's letters on sunspots were published the next year, with his observations set against those of Christoph Scheiner, who held that the spots were small bodies orbiting the Sun. The dispute between the two men over the spots is documented in several printed letters.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.PRACTICE,
        section = "Surveying",
        title = "Snellius's measurement of a meridian by triangles",
        originalTitle = "Eratosthenes Batavus",
        author = "Willebrord Snellius",
        year = 1615,
        summary = "Snellius measured an arc of the meridian in Holland by a chain of triangles, from a measured baseline, and used the angles and the baseline to fix the length of the arc.",
        details = "He made the measurement in 1615 between Alkmaar and Bergen op Zoom, and he published the method in the Eratosthenes Batavus of 1617. The result was used as an early estimate of the size of the Earth, and the method is an ancestor of the later triangulations of nations.",
        rare = true,
    ),

    entry(
        databaseId = 43,
        kind = Kind.PRACTICE,
        section = "Measuring",
        title = "Kepler's gauging of wine casks",
        originalTitle = "Nova stereometria doliorum vinariorum",
        author = "Johannes Kepler",
        year = 1615,
        summary = "Kepler gauged wine casks by measuring a few widths and summing the volumes of thin slices, so that a barrel's content could be found without emptying it.",
        details = "Kepler bought wine casks at Linz in 1615 and found that the usual gauging rules gave the wrong content for the curved shapes, and he set out his own rules in the Nova stereometria doliorum vinariorum of the same year. His method of slices anticipated the sums that later led to the calculus.",
    ),

)
