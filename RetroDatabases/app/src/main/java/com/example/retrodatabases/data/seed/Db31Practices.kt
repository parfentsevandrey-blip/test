package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1730 to 1740: an arc measured in Lapland, pressure in a mare's artery, insect records, electricity and aurora sightings. */
val db31Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 31,
        kind = Kind.PRACTICE,
        section = "Geodesy",
        title = "Maupertuis's arc measurement in Lapland",
        originalTitle = "La figure de la terre",
        author = "Pierre Louis Moreau de Maupertuis",
        year = 1736,
        summary = "A party measured a degree of the meridian in Lapland, and the longer degree toward the pole supported a flattened Earth.",
        details = "Maupertuis led the expedition in 1736 and 1737, and reported the result in his book of 1738. The degree measured far north was longer than the one measured in France, which suggested that the Earth is wider at the equator.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.PRACTICE,
        section = "Physiology",
        title = "Hales's blood pressure in a mare",
        originalTitle = "Statical Essays: Haemastaticks",
        author = "Stephen Hales",
        year = 1733,
        summary = "A glass tube joined to an artery of a mare showed that blood rises in a column, which gave the first recorded measure of blood pressure.",
        details = "Hales published the experiment in Haemastaticks, part of his Statical Essays of 1733. He fixed a brass pipe into the artery and joined a long glass tube, and the blood rose to about eight feet.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.PRACTICE,
        section = "Entomology",
        title = "Reaumur's insect memoirs",
        originalTitle = "Memoires pour servir a l'histoire des insectes",
        author = "Rene-Antoine Ferchault de Reaumur",
        year = 1734,
        summary = "A multi-volume record of insects with careful drawings and dated observations of their life cycles, which set a standard for observation.",
        details = "The first volume of the memoirs appeared in 1734, and the series ran through several volumes until 1742. Its method of dated observation, measurement and drawing made it a model for later naturalists.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.PRACTICE,
        section = "Electricity",
        title = "Du Fay's two kinds of electricity",
        originalTitle = "Memoires de l'Academie royale des sciences",
        author = "Charles Francois de Cisternay du Fay",
        year = 1733,
        summary = "Du Fay proposed that electricity comes in two kinds, vitreous and resinous, which repel their own kind and attract the other.",
        details = "Du Fay reported the two kinds in papers to the Paris Academy in 1733 and 1734, from experiments with rubbed glass and resin. Accounts differ on credit for the first clear observation, and Franklin later proposed a single fluid instead.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.PRACTICE,
        section = "Meteorology",
        title = "Mairan's compilation of aurora sightings",
        originalTitle = "Traite physique et historique de l'aurore boreale",
        author = "Jean-Jacques Dortous de Mairan",
        year = 1733,
        summary = "Dortous de Mairan gathered dated sightings of the northern lights from observers in many places and set them against a theory of their cause.",
        details = "His treatise on the aurora borealis appeared in Paris in 1733, and it combined reports of the lights from many places with an account of their physical cause. Collecting dated reports from correspondents in this way was a practice that later observers of the aurora also followed.",
    ),
)
