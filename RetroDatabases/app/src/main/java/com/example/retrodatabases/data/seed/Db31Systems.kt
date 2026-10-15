package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1730 to 1740: a meridian survey in Quito, a Swedish academy, a German university, a Russian survey and an Irish society. */
val db31Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 31,
        kind = Kind.SYSTEM,
        section = "Geodesy",
        title = "La Condamine's meridian survey in Quito",
        originalTitle = "Mesure des trois premiers degres du meridien dans l'hemisphere austral",
        author = "Charles-Marie de La Condamine",
        year = 1735,
        summary = "A French expedition measured a degree of the meridian near the equator in the Andes, to test whether the Earth bulges at the equator.",
        details = "The expedition reached the Andes in 1735 and worked in the Quito region until about 1744, and its account of the survey was printed in 1751. Its arc was set against the Lapland arc of Maupertuis, and the two together favoured a flattened Earth.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "The Royal Swedish Academy of Sciences",
        originalTitle = "Kungliga Vetenskapsakademien",
        author = "Carl Linnaeus and other Swedish scientists",
        year = 1739,
        summary = "A society of Swedish scientists founded in Stockholm in 1739 to publish research in natural history, mathematics and astronomy.",
        details = "The academy was founded in Stockholm in 1739 by a group of scientists that included Linnaeus. Its volumes of Handlingar carried papers in the sciences from its first years, and the academy is still active.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Gottingen",
        originalTitle = "Georgia Augusta",
        author = "George II of Great Britain as Elector of Hanover",
        year = 1734,
        summary = "A German university founded by royal charter in 1734, which opened for teaching in 1737 and taught natural philosophy alongside law and theology.",
        details = "The charter was granted in 1734, and the university opened for lectures in 1737. Its faculty of philosophy taught mathematics and natural philosophy from the start.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.SYSTEM,
        section = "Exploration",
        title = "The Great Northern Expedition of Russia",
        originalTitle = "The Great Northern Expedition",
        author = "Vitus Bering and Alexei Chirikov",
        year = 1733,
        summary = "A Russian state expedition that charted the Arctic coast of Siberia and the route toward the Pacific during the 1730s.",
        details = "The expedition began in 1733 and ran to about 1743, with parties working along the northern coast of Siberia. Its charts were made in pieces by separate groups, and their results were uneven.",
        rare = true,
    ),

    entry(
        databaseId = 31,
        kind = Kind.SYSTEM,
        section = "Societies",
        title = "The Dublin Society for improving husbandry",
        originalTitle = "Dublin Society for improving husbandry, manufactures and other useful arts",
        author = "Samuel Madden and others",
        year = 1731,
        summary = "An Irish society founded in 1731 to reward and promote farming, manufactures and useful arts, and the forerunner of the Royal Dublin Society.",
        details = "The society was formed in Dublin in 1731 and offered premiums for improvements, including mechanical devices. It later received royal patronage, which gave it the name Royal Dublin Society.",
    ),
)
