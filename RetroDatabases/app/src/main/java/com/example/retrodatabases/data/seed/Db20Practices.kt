package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1840 to 1850: railway time, a planetary prediction, hospital hygiene, anaesthesia and geomagnetic term days. */
val db20Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 20,
        kind = Kind.PRACTICE,
        section = "Time standards",
        title = "Railway time in Britain",
        originalTitle = "Railway time in Britain",
        author = "Railway Clearing House and the railway companies",
        year = 1847,
        summary = "In late 1847 railway companies began to run timetables on London time, so that a train kept one clock across the country.",
        details = "The Railway Clearing House told member stations to adopt London time once the Post Office had approved it, and two large companies changed on 1 December 1847. Exeter kept its own local time until November 1852, and the change never became law.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.PRACTICE,
        section = "Astronomical prediction",
        title = "Le Verrier's prediction of Neptune",
        originalTitle = "Recherches sur les mouvements d'Uranus",
        author = "Urbain Jean Joseph Le Verrier",
        year = 1846,
        summary = "Le Verrier calculated from the disturbed orbit of Uranus where an unseen planet must lie, and Galle found Neptune close to that place in 1846.",
        details = "Le Verrier's work used perturbation calculations, and Galle observed the planet at Berlin in September 1846. John Couch Adams had made a similar calculation independently.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.PRACTICE,
        section = "Hospital hygiene",
        title = "Semmelweis's chlorinated handwashing order",
        originalTitle = "Chlorinated handwashing order at the Vienna clinic",
        author = "Ignaz Semmelweis",
        year = 1847,
        summary = "In May 1847 Semmelweis ordered doctors to wash their hands in chlorinated lime before examining patients, and deaths from childbed fever fell sharply.",
        details = "He had noticed that the clinic staffed by doctors had a higher death rate than the midwives' clinic. He traced the difference to infection carried on the hands.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.PRACTICE,
        section = "Surgery",
        title = "Ether anaesthesia demonstration in Boston",
        originalTitle = "Ether demonstration at the Massachusetts General Hospital",
        author = "William Thomas Green Morton",
        year = 1846,
        summary = "On 16 October 1846 Morton gave a public demonstration of ether anaesthesia in Boston, and news of painless surgery spread quickly.",
        details = "The demonstration took place at the Massachusetts General Hospital. Priority for the discovery was disputed among Morton, Jackson and Wells.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.PRACTICE,
        section = "Geomagnetism",
        title = "Magnetic Crusade term days",
        originalTitle = "Magnetic Crusade, synchronised term-day observations",
        author = "Edward Sabine and observers across the world",
        year = 1840,
        summary = "Sabine's Magnetic Crusade arranged magnetic observations on the same term days at observatories across the world, so the readings could be compared.",
        details = "The method followed the term days that Gauss and Weber had set up in Germany. The campaign ran through the early 1840s.",
        rare = true,
    ),
)
