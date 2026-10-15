package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1860 to 1870: pneumatic despatch, overland and military telegraphs, and the state telegraph of Britain. */
val db18Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 18,
        kind = Kind.SYSTEM,
        section = "Pneumatic despatch",
        title = "London pneumatic despatch, the Euston to Holborn line",
        originalTitle = "London Pneumatic Despatch Company, Euston to Holborn tube (1865)",
        author = "London Pneumatic Despatch Company",
        year = 1865,
        summary = "Mail cars were pushed through an iron tube under central London by compressed air, so letters and parcels could cross the city without carts.",
        details = "The company was formed in 1859 by Thomas Webster Rammell and Latimer Clark, and a short line from Euston to Eversholt Street had run from February 1863. The Euston to Holborn line opened in October 1865, which is the date used here, and the next leg toward the General Post Office was held up by the financial crisis of 1866.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.SYSTEM,
        section = "Overland telegraph",
        title = "Russian-American Telegraph, the overland route by the Bering Strait",
        originalTitle = "Russian-American Telegraph, Collins Overland Telegraph project (1865)",
        author = "Western Union Telegraph Company",
        year = 1865,
        summary = "A telegraph line was to run from North America across the Bering Strait and through Siberia, linking with the European network by an overland route instead of under the sea.",
        details = "The project began in 1865, and work went ahead in north-western North America before it was given up. The Atlantic cable of 1866 succeeded, which made the overland route unnecessary, and construction stopped in 1867.",
        rare = true,
    ),

    entry(
        databaseId = 18,
        kind = Kind.SYSTEM,
        section = "Military telegraph",
        title = "US Military Telegraph, wire orders for the Union army",
        originalTitle = "Military Telegraph Service of the United States Army (1862)",
        author = "Anson Stager and the War Department",
        year = 1862,
        summary = "A telegraph service run by the Union army carried orders between Washington and the field armies, with line crews and operators following the armies into the field.",
        details = "The service was organised in 1862 under Anson Stager, and its crews built and worked lines near the armies as they moved. Accounts differ on the number of miles built and the number of operators employed, so the record gives no totals.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.SYSTEM,
        section = "Telegraph",
        title = "British telegraphs brought under the Post Office",
        originalTitle = "Telegraph Act 1868, purchase of the private telegraph companies",
        author = "Parliament of the United Kingdom",
        year = 1868,
        summary = "An Act of Parliament let the Post Office buy the private telegraph companies, so that messages across Britain passed through one state-run network.",
        details = "The Telegraph Act was passed in 1868, and the Post Office took over the companies in February 1870. The Act answered earlier demands for cheaper and uniform telegraph rates. The record uses the year of the Act, and the takeover date marks the start of the national service.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.SYSTEM,
        section = "Pneumatic despatch",
        title = "Paris pneumatic post, the pneumatique",
        originalTitle = "Pneumatic tube post of the Paris Post Office, the pneumatique (1866)",
        author = "Paris Post Office",
        year = 1866,
        summary = "Letters were sent through a network of iron tubes under Paris by compressed air, giving the city a fast internal post beside the carriage and courier services.",
        details = "Most histories date the start of the Paris service to 1866. It is a separate system from the London despatch of 1865, with its own operator, routes and stations.",
    ),

)
