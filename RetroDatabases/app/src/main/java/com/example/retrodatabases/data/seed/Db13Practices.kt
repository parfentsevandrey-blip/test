package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1910 to 1920: scientific management, a wage rule, radio licensing, group testing and daylight saving. */
val db13Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 13,
        kind = Kind.PRACTICE,
        section = "Management",
        title = "Scientific management",
        originalTitle = "The Principles of Scientific Management (Taylor, 1911)",
        author = "Frederick Winslow Taylor",
        year = 1911,
        summary = "A method of setting work by timing each step with a stopwatch, so that a benchmark time for each task replaces the foreman's judgment.",
        details = "The first edition was printed privately in February 1911 for members of the American Society of Mechanical Engineers, and the commercial edition followed in the same year. The benchmark times served as the basis for pay and output targets.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.PRACTICE,
        section = "Wages",
        title = "The five-dollar day",
        originalTitle = "Minimum wage of five dollars for an eight-hour shift at the Ford Motor Company, announced January 1914",
        author = "Henry Ford and the Ford Motor Company",
        year = 1914,
        summary = "On 5 January 1914 Ford set a minimum of five dollars a day for an eight-hour shift, more than twice the usual wage, and added profit sharing for eligible workers.",
        details = "Access to the higher rate depended on a company sociological department that judged workers' home lives. Men received it first, and women from October 1916. Accounts give several motives, among them reducing turnover, blunting union organizing and creating buyers for the cars. Staff departures fell, and profits roughly doubled between 1914 and 1916.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.PRACTICE,
        section = "Licensing",
        title = "The Radio Act of 1912",
        originalTitle = "Radio Act of 1912, approved 13 August 1912",
        author = "United States Congress",
        year = 1912,
        summary = "The first federal radio law, which required licenses for stations and for operators, reserved a distress frequency and gave distress calls priority.",
        details = "The act followed the sinking of the Titanic and the Wireless Ship Act of 1910. The Commerce secretary issued the licenses, and government stations and their operators were outside the requirement. The Radio Act of 1927 superseded it with broader federal powers.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.PRACTICE,
        section = "Testing",
        title = "The Army Alpha test",
        originalTitle = "Army Alpha and Beta group intelligence tests (Yerkes and colleagues, 1917)",
        author = "Robert Mearns Yerkes and colleagues",
        year = 1917,
        summary = "A written group test given to literate United States Army recruits in World War I, scored in bulk so that men could be sorted for duties and for officer training.",
        details = "Work on the tests began in May 1917. The Beta test used pictures for men who could not read English well. Finished editions appeared in January 1919. Estimates of the number of men tested run from more than one million to close to two million, and later reviewers argued that the scores mostly reflected years spent in the United States.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.PRACTICE,
        section = "Time",
        title = "Daylight saving time in Germany",
        originalTitle = "Summer time in Germany, the first nationwide daylight saving (1916)",
        author = "German government",
        year = 1916,
        summary = "On 30 April 1916 Germany set its clocks an hour ahead to save fuel during the war, the first nationwide use of daylight saving time.",
        details = "Austria-Hungary, Britain and France began similar schemes in 1916, and Britain's began in May under the name British Summer Time. A Canadian municipality had tried seasonal clock changes in 1908. The practice lapsed after the war and returned in World War II.",
    ),
)
