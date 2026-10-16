package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1420 to 1430: a catasto tally of declared wealth, a ledger of bricks for the dome, a cart train for a siege and a shelf count for a royal hall's books. */
val db62Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 62,
        kind = Kind.PROJECT,
        section = "Taxation",
        title = "A catasto tally of declared wealth",
        originalTitle = "Catasto",
        author = "Florentine tax officers",
        year = 1427,
        summary = "A tally of household declarations in the catasto of 1427 adds up the wealth declared, counts the households and finds the average, with exact fractions.",
        details = "The catasto of 1427 asked each household to declare its property. The example tallies four illustrative declarations, because the figures of real households are not used here.",
        instructions = """
            1. Save the example as catasto_tally.py.
            2. Run it: python3 catasto_tally.py
        """,
        example = """
            from fractions import Fraction

            declared = [1200, 350, 4800, 0]   # illustrative declarations in florins
            total = sum(declared)
            print(f"households: {len(declared)}")
            print(f"total declared: {total} florins")
            print(f"average: {Fraction(total, len(declared))} florins")
            print(f"without declared wealth: {declared.count(0)}")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.PROJECT,
        section = "Building accounts",
        title = "A ledger of bricks for the rings of the dome",
        originalTitle = "Libro dei mattoni",
        author = "The works of the cathedral",
        year = 1420,
        summary = "A ledger of the bricks laid in each ring of the dome totals the rings, finds the average and names the largest ring, using illustrative counts.",
        details = "The dome of Florence was begun in 1420, and its accounts counted bricks by course. The example uses five illustrative ring counts, not the figures of the cathedral's records.",
        instructions = """
            1. Save the example as brick_ledger.py.
            2. Run it: python3 brick_ledger.py
        """,
        example = """
            from fractions import Fraction

            rings = [400, 420, 440, 460, 480]   # illustrative bricks laid in each ring
            total = sum(rings)
            largest = max(range(len(rings)), key=lambda i: rings[i])
            print(f"rings: {len(rings)}")
            print(f"total bricks: {total}")
            print(f"average per ring: {Fraction(total, len(rings))}")
            print(f"largest ring: ring {largest + 1} with {rings[largest]} bricks")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.PROJECT,
        section = "Field supply",
        title = "A cart train for a siege supply",
        originalTitle = "Carro di rifornimento",
        author = "Hussite and Italian army quartermasters",
        year = 1420,
        summary = "A supply train of carts for a siege needs enough carts for its load, which is found by rounding the load up to whole carts, and a short program does the division for three loads.",
        details = "Wagon trains of the 1420s carried food, powder and shot, and the quartermaster counted the carts first. The example uses three illustrative loads and cart capacities, chosen for the example.",
        instructions = """
            1. Save the example as cart_train.py.
            2. Run it: python3 cart_train.py
        """,
        example = """
            def carts_needed(load, capacity):
                # Round the load up to whole carts: a partly full cart still needs its own place.
                return -(-load // capacity)

            for load, capacity in [(1000, 180), (1500, 250), (2600, 400)]:
                print(f"load {load} with carts of {capacity}: {carts_needed(load, capacity)} carts")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.PROJECT,
        section = "Libraries",
        title = "Shelving the books of a royal hall",
        originalTitle = "Scaffali della sala",
        author = "The keepers of the royal hall's books",
        year = 1420,
        summary = "A shelf register for a royal hall counts the shelves needed for its volumes and the number left on the last shelf, with illustrative numbers.",
        details = "A hall of learned men kept its books on shelves of fixed length, and the keeper needed the count before a new room was fitted. The example uses 320 illustrative volumes and shelves that each hold 45.",
        instructions = """
            1. Save the example as shelf_register.py.
            2. Run it: python3 shelf_register.py
        """,
        example = """
            volumes = 320
            per_shelf = 45
            shelves = -(-volumes // per_shelf)           # round up: the last shelf may be partly full
            last = volumes - (shelves - 1) * per_shelf   # volumes on the last shelf
            print(f"{volumes} volumes on shelves of {per_shelf} need {shelves} shelves")
            print(f"the last shelf holds {last} volumes")
        """,
    ),

)
