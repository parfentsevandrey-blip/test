package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1910 to 1920: small programs for a moving line, a text Gantt chart, a loaded bar and a nested radical. */
val db13Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 13,
        kind = Kind.PROJECT,
        section = "Production",
        title = "A moving assembly line simulator",
        originalTitle = "Highland Park moving assembly line, chassis line of 1913",
        author = "Written as a teaching example",
        year = 1913,
        summary = "A belt moves each car past four stations at a fixed pace set by the slowest task, so a car leaves every five minutes and the line turns out twelve cars an hour.",
        details = "Ford's chassis line of October 1913 used a belt with one task at each station, and the magneto work of April 1913 had been split into 29 operations. The example uses four stations with work times of 3, 5, 4 and 2 minutes and five cars, and prints the belt pace, the idle time at each station and the times at which the first and the fifth cars leave.",
        instructions = """
            1. Save the example as ford_line.py.
            2. Run it: python3 ford_line.py
        """,
        example = """
            TASKS = [3, 5, 4, 2]   # minutes of work at each station along the line
            CARS = 5
            PACE = max(TASKS)      # the belt moves once the slowest station has finished its task
            stations = len(TASKS)
            leave = []
            for car in range(CARS):
                # Car number car is at station s during slot car + s, and it leaves after the last slot.
                last_slot = car + stations - 1
                leave.append((last_slot + 1) * PACE)
            print("belt pace:", PACE, "minutes")
            print("cars per hour:", 60 // PACE)
            print("idle minutes at each station:", [PACE - t for t in TASKS])
            print("idle minutes per car:", sum(PACE - t for t in TASKS))
            print("first car leaves at minute", leave[0])
            print("fifth car leaves at minute", leave[-1])
            print("one worker per car, five cars:", CARS * sum(TASKS), "minutes")
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.PROJECT,
        section = "Scheduling",
        title = "A Gantt chart drawn in plain text",
        originalTitle = "Gantt's charts of schedules, from his articles of 1910 and 1915",
        author = "Written as a teaching example",
        year = 1910,
        summary = "A Gantt chart puts each task on a time axis as a bar, so the length of a schedule and the overlap of tasks can be read at a glance.",
        details = "Gantt's charts of the 1910s spread the bar of time in the West, after Karol Adamiecki had used a similar chart, his harmonogram, in the 1890s. The example draws four tasks as text bars over eight weeks, with a header line of week numbers, and prints the length of the project.",
        instructions = """
            1. Save the example as gantt.py.
            2. Run it: python3 gantt.py
        """,
        example = """
            TASKS = [
                ("design", 0, 2),
                ("build", 2, 3),
                ("test", 5, 2),
                ("ship", 7, 1),
            ]
            weeks = max(start + length for _, start, length in TASKS)
            print("week:  " + "".join(str(w % 10) for w in range(weeks)))
            for name, start, length in TASKS:
                bar = "." * start + "#" * length + "." * (weeks - start - length)
                print(f"{name:<6} " + bar)
            print(f"project length: {weeks} weeks")
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.PROJECT,
        section = "Elasticity",
        title = "A Galerkin approximation for a loaded bar",
        originalTitle = "Rods and plates, a paper in Vestnik inzhenerov i tekhnikov (Galerkin, 1915)",
        author = "Written as a teaching example",
        year = 1915,
        summary = "Galerkin's method makes the error orthogonal to each basis function, and with one basis function the answer is exact at the midpoint of the bar but not at a quarter point.",
        details = "Galerkin's 1915 paper on rods and plates gave the method, which chooses coefficients so that the error is orthogonal to the basis. The example solves minus u'' equals x on the unit interval, with u zero at both ends, using the single basis function x(1 - x). It evaluates the integrals with Simpson's rule, which is exact for cubics.",
        instructions = """
            1. Save the example as galerkin.py.
            2. Run it: python3 galerkin.py
        """,
        example = """
            from fractions import Fraction as F


            def simpson(g):
                # Simpson's rule on [0, 1] with two panels, which is exact for polynomials up to degree three.
                return (g(F(0)) + 4 * g(F(1, 2)) + g(F(1))) / 6


            stiffness = simpson(lambda x: (1 - 2 * x) ** 2)   # integral of the square of the derivative of x(1 - x)
            load = simpson(lambda x: x * x * (1 - x))         # integral of f(x) = x times the basis function x(1 - x)
            c = load / stiffness
            print("coefficient:", c)
            for x in (F(1, 4), F(1, 2)):
                print(f"x={x}: galerkin {c * x * (1 - x)}, exact {(x - x ** 3) / 6}")
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.PROJECT,
        section = "Algebra",
        title = "Ramanujan's nested radical converging to 3",
        originalTitle = "Question 289, Journal of the Indian Mathematical Society (Ramanujan, 1911)",
        author = "Srinivasa Ramanujan",
        year = 1911,
        summary = "The nested radical with coefficients 2, 3, 4 and so on equals 3, and each level of its inner folding has the exact value k plus 2.",
        details = "Ramanujan posed the nested radical as Question 289 in the Journal of the Indian Mathematical Society in 1911 and gave the answer himself. The example folds outward from the exact tail value 7 and prints the four levels, which are 6, 5, 4 and 3, then checks the identity that holds at every level.",
        rare = true,
        instructions = """
            1. Save the example as nested_radical.py.
            2. Run it: python3 nested_radical.py
        """,
        example = """
            import math

            # Ramanujan's nested radical: 3 = sqrt(1 + 2 sqrt(1 + 3 sqrt(1 + 4 sqrt(...)))).
            # Level k satisfies g(k) = sqrt(1 + (k + 1) g(k + 1)), and the exact value at every level is k + 2.
            levels = []
            g = 7.0   # exact value at level 5, which is 5 + 2
            for k in range(4, 0, -1):
                g = math.sqrt(1 + (k + 1) * g)
                levels.append(g)
            print("levels from the tail 7:", levels)
            print("identity holds for k = 1 to 6:", all(1 + (k + 1) * (k + 3) == (k + 2) ** 2 for k in range(1, 7)))
        """,
    ),
)
