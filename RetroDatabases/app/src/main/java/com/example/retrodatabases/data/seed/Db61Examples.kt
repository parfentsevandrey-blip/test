package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1430 to 1440: lots drawn in a ring of seven, a land-value filter for voters, the year 1437 in three bases, roads between Norman towns, boat loads for a river crossing and the squares of Alberti's grid. */
val db61Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 61,
        kind = Kind.EXAMPLE,
        section = "Lots",
        title = "Lots drawn in a ring of seven, the Josephus count",
        originalTitle = "Compiled for this exercise",
        author = "Compiled for this exercise",
        year = 1436,
        summary = "A short program removes every third person from a ring of seven, and it prints the order of removal and the one who survives.",
        details = "The story is told by the historian Flavius Josephus, who describes a band that drew lots in a ring, and this example works out the same kind of count for seven people. The order of removal and the survivor are fixed, so the output can be checked by hand.",
        instructions = """
            1. Save the example as josephus_ring.py.
            2. Run it: python3 josephus_ring.py
        """,
        example = """
            PEOPLE = 7
            STEP = 3

            ring = list(range(1, PEOPLE + 1))
            order = []
            index = 0
            while len(ring) > 1:
                index = (index + STEP - 1) % len(ring)
                order.append(ring.pop(index))

            print("removal order:", ", ".join(str(p) for p in order))
            print("survivor:", ring[0])
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.EXAMPLE,
        section = "Voting",
        title = "Counting the county voters who held forty shillings of land",
        originalTitle = "Statute of 1430 on county elections",
        author = "The English Parliament",
        year = 1430,
        summary = "Under the forty-shilling rule a county voter needed land worth at least forty shillings a year, and a short program picks out the eligible names from a list of five.",
        details = "The threshold comes from the English rule of 1430 for county voters, and this example applies it to an invented list of names and land values. The names and values are made up for the exercise, so the output shows the filter and not any real county.",
        instructions = """
            1. Save the example as forty_shillings.py.
            2. Run it: python3 forty_shillings.py
        """,
        example = """
            ROLLS = [("Hugh", 40), ("Alice", 39), ("Robert", 52), ("Joan", 20), ("John", 40)]   # name and land value in shillings a year, invented
            THRESHOLD = 40

            eligible = [name for name, value in ROLLS if value >= THRESHOLD]
            print("eligible:", ", ".join(eligible))
            print(f"{len(eligible)} of {len(ROLLS)} would vote")
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.EXAMPLE,
        section = "Number bases",
        title = "The year 1437 written in three number bases",
        originalTitle = "Positional notation",
        author = "Compiled for this exercise",
        year = 1437,
        summary = "The year 1437 has an eleven-digit binary form, and a short program prints the year in binary, octal and hexadecimal.",
        details = "Writing a number in bases two, eight and sixteen is a modern convenience, and the example uses the built-in conversions of Python so that the output is fixed. The year is chosen only because it falls in the decade, and the three forms show how the same count looks in each base.",
        instructions = """
            1. Save the example as year_bases.py.
            2. Run it: python3 year_bases.py
        """,
        example = """
            YEAR = 1437
            print(f"{YEAR} in binary is {bin(YEAR)[2:]}")
            print(f"{YEAR} in octal is {oct(YEAR)[2:]}")
            print(f"{YEAR} in hexadecimal is {hex(YEAR)[2:]}")
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.EXAMPLE,
        section = "Roads",
        title = "The shortest road between three towns of Normandy",
        originalTitle = "Compiled for this exercise",
        author = "Compiled for this exercise",
        year = 1432,
        summary = "A set of roads between three Norman towns can be searched for the shortest route between each pair, and a short program shows that a detour through Caen can beat the direct road.",
        details = "The distances are invented for the exercise, so the output shows the method and not the real roads of Normandy. The program repeats one step over all the towns, and it stops when no detour is shorter than the road already found.",
        instructions = """
            1. Save the example as norman_roads.py.
            2. Run it: python3 norman_roads.py
        """,
        example = """
            TOWNS = ["Bayeux", "Caen", "Falaise"]
            INF = 10 ** 9
            dist = {a: {b: INF for b in TOWNS} for a in TOWNS}
            for a in TOWNS:
                dist[a][a] = 0

            links = [("Caen", "Bayeux", 27), ("Caen", "Falaise", 25), ("Bayeux", "Falaise", 60)]   # miles, invented
            for a, b, d in links:
                dist[a][b] = d
                dist[b][a] = d

            for k in TOWNS:
                for i in TOWNS:
                    for j in TOWNS:
                        if dist[i][k] + dist[k][j] < dist[i][j]:
                            dist[i][j] = dist[i][k] + dist[k][j]

            for a, b in (("Bayeux", "Falaise"), ("Bayeux", "Caen"), ("Caen", "Falaise")):
                print(f"{a} to {b}: {dist[a][b]} miles")
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.EXAMPLE,
        section = "Crossings",
        title = "Loads of pilgrims carried across a river in a small boat",
        originalTitle = "Compiled for this exercise",
        author = "Compiled for this exercise",
        year = 1431,
        summary = "A short program works out how many boat loads it takes to carry a party of pilgrims across a river, and how full the last load is.",
        details = "The party size and the boat's capacity are invented for the exercise, so the output shows the method and not a real crossing. The program fills each load to the boat's capacity until fewer people are left, and the final load is the remainder.",
        instructions = """
            1. Save the example as river_loads.py.
            2. Run it: python3 river_loads.py
        """,
        example = """
            PILGRIMS = 23
            BOAT = 4

            loads = []
            left = PILGRIMS
            while left > 0:
                load = min(BOAT, left)
                loads.append(load)
                left -= load

            print("pilgrims:", PILGRIMS)
            print("boat holds:", BOAT)
            print("loads needed:", len(loads))
            print("people in the last load:", loads[-1])
            print("loads:", loads)
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.EXAMPLE,
        section = "Perspective",
        title = "Squares of every size in a four-by-four grid, as on Alberti's veil",
        originalTitle = "Compiled for this exercise",
        author = "Compiled for this exercise",
        year = 1435,
        summary = "A short program counts the squares of every size in a four-by-four grid, as a painter could count the cells of Alberti's veil, and it prints the total.",
        details = "The veil is a grid of squares, and this example counts every square the grid contains, whatever its size. Squares of side k in an n by n grid number (n - k + 1) squared, and the total is the sum over all the sizes.",
        instructions = """
            1. Save the example as alberti_squares.py.
            2. Run it: python3 alberti_squares.py
        """,
        example = """
            SIDE = 4

            total = 0
            for size in range(1, SIDE + 1):
                count = (SIDE - size + 1) ** 2
                total += count
                print(f"squares of side {size}: {count}")
            print(f"squares in all: {total}")
        """,
    ),

)
