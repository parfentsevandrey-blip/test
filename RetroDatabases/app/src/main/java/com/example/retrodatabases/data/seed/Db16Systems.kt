package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1880 to 1890: early public power and electric transport networks, each described from its opening. */
val db16Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 16,
        kind = Kind.SYSTEM,
        section = "Power stations",
        title = "Edison's Pearl Street central station",
        originalTitle = "Pearl Street Station",
        author = "Edison Electric Illuminating Company of New York",
        year = 1882,
        summary = "The first central station in New York that supplied direct-current light and power to customers through a network of underground mains.",
        details = "The station began service on 4 September 1882 in lower Manhattan, with steam engines driving Edison dynamos. Sources differ on how many customers it had at the start, so this record gives no count. It is the usual starting point for the history of the public electricity supply.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.SYSTEM,
        section = "Power distribution",
        title = "The Great Barrington alternating-current system",
        originalTitle = "Great Barrington alternating current system",
        author = "William Stanley for George Westinghouse",
        year = 1886,
        summary = "A small alternating-current lighting system in Massachusetts, with transformers that stepped the voltage up for the line and down for the lamps.",
        details = "From 20 March 1886 the system lit shops and offices along Main Street in Great Barrington, using transformers of the Gaulard and Gibbs type that William Stanley built. Sources disagree on which installation was the first commercial one, so the record claims no such status. The generator was unreliable, and the system did not last long.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.SYSTEM,
        section = "Electric transport",
        title = "The Gross-Lichterfelde electric tramway",
        originalTitle = "Gross-Lichterfelde Tramway",
        author = "Siemens and Halske",
        year = 1881,
        summary = "An electric tramway in a Berlin suburb that carried passengers from May 1881, and the first regular public electric tram in service.",
        details = "The line opened on 16 May 1881 at Lichterfelde near Berlin, and its current first came through one of the rails, with overhead wires added in 1883. Fedir Pirotsky had shown an earlier experimental electric tram in St Petersburg, so the word first depends on the definition used. The record gives the first regular public service.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.SYSTEM,
        section = "Electric transport",
        title = "Richmond electric streetcars under Sprague",
        originalTitle = "Richmond Union Passenger Railway",
        author = "Frank J. Sprague",
        year = 1888,
        summary = "A street-car system in Richmond, Virginia, run by motors on the cars under an overhead trolley wire, and an early large success for the electric street railway.",
        details = "Sprague equipped the Richmond line early in 1888 with motors on the cars and a trolley pole on overhead wires. Its success led to a quick spread of electric street railways in the United States. The record covers only the 1888 installation.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.SYSTEM,
        section = "Electric transport",
        title = "The telpher line at Glynde",
        originalTitle = "Telpher line at Glynde",
        author = "Fleeming Jenkin, with Ayrton and Perry",
        year = 1885,
        summary = "A line on which electrically driven cars carried clay on a single track, opened in Sussex in October 1885.",
        details = "The line opened on 17 October 1885, carrying clay about a mile from a pit to the railway station at Glynde. Jenkin died in June 1885, and Perry completed the work after him. Sources agree on the opening date but name different cement companies as the customer, so the record names none.",
        rare = true,
    ),
)
