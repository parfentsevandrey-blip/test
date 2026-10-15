package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1820 to 1830: the first steam railways, a canal, a city omnibus service and a national survey. */
val db22Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 22,
        kind = Kind.SYSTEM,
        section = "Railways",
        title = "Stockton and Darlington Railway",
        originalTitle = "Stockton and Darlington Railway",
        author = "Edward Pease and George Stephenson",
        year = 1825,
        summary = "The first public railway to carry goods and passengers with steam locomotives, opened in 1825 between Stockton and Darlington in County Durham.",
        details = "The line opened on 27 September 1825, and Locomotion No. 1 hauled the first train, which carried passengers and coal. Horses still pulled much of the traffic for years, and accounts differ on how much of the 1825 traffic was steam-hauled.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.SYSTEM,
        section = "Railways",
        title = "Liverpool and Manchester Railway",
        originalTitle = "Liverpool and Manchester Railway",
        author = "George Stephenson and the company's directors",
        year = 1830,
        summary = "The first railway between two cities to run a timetable with steam locomotives for both passengers and freight, opened in 1830.",
        details = "The Rainhill trials of October 1829 chose Stephenson's Rocket for the line, which opened on 15 September 1830. On the opening day William Huskisson, a former cabinet minister, was struck by a locomotive and died of his injuries.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.SYSTEM,
        section = "Canals",
        title = "Erie Canal",
        originalTitle = "Erie Canal",
        author = "New York canal commissioners",
        year = 1825,
        summary = "A canal from the Hudson River to Lake Erie that opened in 1825 and joined New York City to the Great Lakes by water.",
        details = "The canal opened on 26 October 1825 after eight years of construction, and boats used locks to climb from the Hudson up to the lake level and to descend again. Its success led other states to build canals of their own.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.SYSTEM,
        section = "Urban transport",
        title = "Baudry's omnibus service in Paris",
        originalTitle = "Omnibus service in Paris (1828)",
        author = "Stanislas Baudry",
        year = 1828,
        summary = "Horse-drawn omnibuses ran on fixed routes at a fixed fare, so any passenger could board at a stop without hiring a whole carriage.",
        details = "Baudry had run a similar service at Nantes from 1826, and the Paris service began in 1828. Its fixed routes and fares made it a public service rather than a hire.",
        rare = true,
    ),

    entry(
        databaseId = 22,
        kind = Kind.SYSTEM,
        section = "Surveying",
        title = "Gauss's Hanover triangulation",
        originalTitle = "Triangulation of the Kingdom of Hanover (1821 to 1825)",
        author = "Carl Friedrich Gauss",
        year = 1821,
        summary = "Gauss ran a chain of measured triangles across the Kingdom of Hanover from 1821, fixing the positions of survey points by the angles between them.",
        details = "The survey used a heliotrope to flash sunlight between distant stations, so that sightings could be made over long lines. The field work ran from 1821 to 1825, and the geodesy from it fed into Gauss's 1827 study of curved surfaces.",
    ),
)
