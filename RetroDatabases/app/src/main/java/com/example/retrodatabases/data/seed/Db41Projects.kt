package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1630 to 1640: a projectile's range table, running totals of weekly counts, a matching office and a letter route. */
val db41Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 41,
        kind = Kind.PROJECT,
        section = "Mechanics",
        title = "Galileo's projectile range table",
        originalTitle = "Discorsi e dimostrazioni matematiche intorno a due nuove scienze",
        author = "Galileo Galilei",
        year = 1638,
        summary = "Galileo's 1638 account of thrown bodies gives a parabolic path whose range is greatest at 45 degrees and the same for two elevations that add to 90 degrees.",
        details = "The Two New Sciences of 1638 treats the path of a projectile as the sum of a uniform horizontal motion and a uniformly accelerated fall, which gives a parabola. The program prints the range for five elevations at a fixed speed, so the equal ranges at 30 and 60 degrees, and the greatest range at 45, can be checked.",
        instructions = """
            1. Save the example as projectile.py.
            2. Run it: python3 projectile.py
        """,
        example = """
            import math

            v = 10.0    # speed in metres per second
            g = 9.8     # gravity in metres per second squared
            for theta in (15, 30, 45, 60, 75):
                # The range on level ground is v squared times the sine of twice the elevation, over g.
                r = v * v * math.sin(math.radians(2 * theta)) / g
                print(f"elevation {theta} degrees: range {r:.2f} metres")
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.PROJECT,
        section = "Records",
        title = "Running totals and the busiest week in a list of weekly counts",
        originalTitle = "Weekly counts, invented figures",
        author = "Invented figures",
        year = 1636,
        summary = "A program that keeps a running total of weekly counts and reports the busiest week and the average shows the simple arithmetic that a registry of weekly counts needs.",
        details = "The counts are invented, since the example is about the arithmetic and not a real parish. The year marks the decade in which London's weekly bills of mortality were printed, which is why the record sits in the 1630s.",
        instructions = """
            1. Save the example as weekly_counts.py.
            2. Run it: python3 weekly_counts.py
        """,
        example = """
            counts = [12, 9, 15, 11, 14, 10]

            total = 0
            for week, count in enumerate(counts, start=1):
                total += count
                print(f"week {week}: {count} counted, running total {total}")
            busiest = counts.index(max(counts)) + 1
            print(f"highest week: {busiest} with {max(counts)} counted")
            print(f"average per week: {total / len(counts):.2f}")
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.PROJECT,
        section = "Information",
        title = "Matching requests to offers in the order the clerks received them",
        originalTitle = "Bureau d'adresse et de rencontre",
        author = "Theophraste Renaudot",
        year = 1630,
        summary = "A program that serves each request in the order received, giving it a place while one is free, shows a simple way for a clerk to match requests with offers.",
        details = "The rule is a toy: it serves requests in the order they arrive and gives each one a free place, and Renaudot's own rules are not reproduced here. The year is that of the office's opening, and the names and places are invented.",
        instructions = """
            1. Save the example as bureau_match.py.
            2. Run it: python3 bureau_match.py
        """,
        example = """
            offers = {"tutor": 2, "clerk": 1, "physician": 1}
            requests = [("Anne", "clerk"), ("Pierre", "tutor"), ("Marie", "tutor"), ("Jean", "physician"), ("Luc", "tutor")]

            placed = 0
            for name, wanted in requests:
                if offers.get(wanted, 0) > 0:
                    offers[wanted] -= 1
                    placed += 1
                    print(f"{name}: {wanted}")
                else:
                    print(f"{name}: waiting for a {wanted}")
            print(f"placed {placed} of {len(requests)} requests")
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.PROJECT,
        section = "Correspondence",
        title = "The fewest hops for a letter between two cities, by breadth-first search",
        originalTitle = "Fewest hops for a letter between two cities",
        author = "Invented network",
        year = 1636,
        summary = "A breadth-first search finds the fewest relay hops a letter needs between two cities in a small network, the question a correspondent faced when choosing how to send a letter.",
        details = "The network is a toy with five cities and invented links, not the real routes of Mersenne's letters. The search records the first route found to each city, and that route has the fewest hops.",
        instructions = """
            1. Save the example as letter_route.py.
            2. Run it: python3 letter_route.py
        """,
        example = """
            from collections import deque

            links = {
                "Paris": ["Leiden", "Rome"],
                "Leiden": ["Paris", "Florence"],
                "Rome": ["Paris", "Florence"],
                "Florence": ["Leiden", "Rome", "Toulouse"],
                "Toulouse": ["Florence"],
            }

            def route(start, goal):
                parent = {start: None}
                queue = deque([start])
                while queue:
                    city = queue.popleft()
                    for nxt in links[city]:
                        if nxt not in parent:
                            parent[nxt] = city
                            queue.append(nxt)
                path = []
                city = goal
                while city is not None:
                    path.append(city)
                    city = parent[city]
                return list(reversed(path))

            path = route("Paris", "Toulouse")
            print(f"fewest hops from Paris to Toulouse: {len(path) - 1}")
            print("one route: " + ", ".join(path))
        """,
    ),

)
