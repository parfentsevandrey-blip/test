package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guide of 830: how a gospel harmony shows which of the four gospels tell each episode, counted in code. */
val db121Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 121,
        kind = Kind.GUIDE,
        section = "Gospels",
        title = "How a gospel harmony shows which of the four gospels tell each episode, counted in code",
        originalTitle = "How a gospel harmony shows which of the four gospels tell each episode, counted in code",
        author = "Makers of gospel harmonies",
        year = 830,
        summary = "A gospel harmony places the four accounts of an episode side by side, and a short program counts how many of the four gospels tell each of five episodes.",
        details = "The Heliand, dated about 830, weaves the four gospels into one story, so 830 is the poem's date and not a date given for the method. The example lists five episodes with the gospels that tell each one, then prints how many gospels tell each episode and how many episodes are told by all four or by only three.",
        instructions = """
            1. Save the example as harmony.py.
            2. Run it: python3 harmony.py
        """,
        example = """
            GOSPELS = ["Matthew", "Mark", "Luke", "John"]
            EPISODES = [
                ("feeding of the five thousand", {"Matthew", "Mark", "Luke", "John"}),
                ("walking on the water", {"Matthew", "Mark", "John"}),
                ("transfiguration", {"Matthew", "Mark", "Luke"}),
                ("cleansing of the temple", {"Matthew", "Mark", "Luke", "John"}),
                ("triumphal entry into Jerusalem", {"Matthew", "Mark", "Luke", "John"}),
            ]
            for name, told in EPISODES:
                print(f"{name}: {len(told)} gospels")
            print("told by all four:", sum(1 for _, told in EPISODES if len(told) == len(GOSPELS)))
            print("told by only three:", sum(1 for _, told in EPISODES if len(told) == 3))
        """,
        rare = true,
    ),

)
