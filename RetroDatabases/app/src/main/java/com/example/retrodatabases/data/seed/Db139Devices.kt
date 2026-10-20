package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 650 to 660: Japan's first water clock, which the Nihon Shoki places in 660 and which may have stood at Asuka Mizuochi. */
val db139Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 139,
        kind = Kind.DEVICE,
        section = "Japanese",
        title = "Japan's first water clock, which the Nihon Shoki places in 660, with its possible site at Asuka Mizuochi",
        originalTitle = "Asuka Mizuochi",
        author = "Prince Naka-no-Oe, later Emperor Tenji",
        year = 660,
        summary = "The Nihon Shoki credits Prince Naka-no-Oe, later Emperor Tenji, with Japan's first water clock in 660, and the Asuka Mizuochi site has been linked with it.",
        details = "Excavations at Asuka Mizuochi from 1981 to 1986 found a stone base, lacquered wooden boxes and copper pipes, and the link with the 660 clock is a strong possibility rather than a settled fact. The Nihon Shoki also records a clock set up in a new observatory in 671, after its maker became emperor.",
        rare = true,
    ),

)
