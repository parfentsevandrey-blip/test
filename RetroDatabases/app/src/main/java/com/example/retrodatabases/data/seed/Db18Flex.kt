package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1860 to 1870: a rule of logical substitution, the exterior product of vectors and the line coordinates of geometry. */
val db18Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 18,
        kind = Kind.FLEX,
        section = "Logic notation",
        title = "Jevons's substitution of similars on a chain of classes",
        originalTitle = "Pure Logic, or the Logic of Quality apart from Quantity (1864)",
        author = "William Stanley Jevons",
        year = 1864,
        summary = "A class written as a product of terms can have one term replaced by an equal class, so a chain of inclusions gives a conclusion by substitution alone.",
        details = "Jevons's rule of substitution of similars appears in his 1864 Pure Logic, where a class is written as a product of terms, such as AB for the things that are both A and B. The example takes three classes as sets of numbers, checks the two premises, substitutes BC for B, and tests the conclusion, and it also shows a premise that fails.",
        instructions = """
            1. Save the example as jevons_substitution.py.
            2. Run it: python3 jevons_substitution.py
        """,
        example = """
            # Classes as sets of numbered objects. Jevons writes a class as a product of terms:
            # AB is the set of things that are both A and B.
            A = {1, 2, 3, 4}
            B = {1, 2, 3, 4, 5, 6}
            C = {1, 2, 3, 4, 5, 6, 7, 8}
            E = {1, 9}

            print("premise A = AB holds:", A == A & B)
            print("premise B = BC holds:", B == B & C)
            # Substitution of similars: B equals BC, so B inside AB can be replaced by BC.
            print("AB with B replaced by BC equals A:", A == A & (B & C))
            print("conclusion A = AC holds:", A == A & C)
            print("class E = EC fails, as it should:", E == E & C)
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.FLEX,
        section = "Vector algebra",
        title = "Grassmann's exterior product of two vectors in space",
        originalTitle = "Die Ausdehnungslehre, vollstaendig und in strenger Form bearbeitet (1862)",
        author = "Hermann Grassmann",
        year = 1862,
        summary = "The product of two vectors is a bivector whose components are the two by two minors, and swapping the vectors changes the sign, so a vector wedged with itself is zero.",
        details = "Grassmann's Ausdehnungslehre of 1844 was little read, and the second, reworked edition of 1862 set out the exterior product in the form used later. The example computes the product of two vectors in three dimensions and checks that swapping them changes the sign and that a vector with itself gives zero.",
        instructions = """
            1. Save the example as grassmann_wedge.py.
            2. Run it: python3 grassmann_wedge.py
        """,
        example = """
            from itertools import combinations

            # Exterior product of two vectors in three dimensions: one component for each pair of basis vectors,
            # given by the two by two minors of the matrix whose rows are the vectors.
            def wedge(a, b):
                return {(i, j): a[i] * b[j] - a[j] * b[i] for i, j in combinations(range(3), 2)}

            a = (1, 2, 0)
            b = (0, 1, 3)
            ab = wedge(a, b)
            ba = wedge(b, a)
            print("a wedge b:", [ab[k] for k in sorted(ab)])
            print("b wedge a:", [ba[k] for k in sorted(ba)])
            print("swapping changes the sign:", all(ab[k] == -ba[k] for k in ab))
            print("a wedge a is zero:", all(v == 0 for v in wedge(a, a).values()))
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.FLEX,
        section = "Line geometry",
        title = "Plucker's line coordinates and the test for meeting lines",
        originalTitle = "On a New Geometry of Space (1865)",
        author = "Julius Plucker",
        year = 1865,
        summary = "A line in space is described by a direction and a moment, so that the test for two lines meeting becomes a sum of products of whole numbers.",
        details = "Plucker's 1865 paper introduced these line coordinates, which turn questions about lines in space into arithmetic on six numbers. Two lines meet, or are parallel, exactly when the reciprocal product of their coordinates is zero. The example computes the coordinates of four lines through pairs of points with whole-number coordinates, prints them, and tests which pairs meet.",
        instructions = """
            1. Save the example as plucker.py.
            2. Run it: python3 plucker.py
        """,
        example = """
            # A line through points P and Q has Plucker coordinates (d, m): direction d = Q - P and moment m = P x Q.
            def cross(u, v):
                return (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])

            def dot(u, v):
                return sum(a * b for a, b in zip(u, v))

            def plucker(P, Q):
                d = tuple(q - p for p, q in zip(P, Q))
                return d, cross(P, Q)

            def meet(L1, L2):
                # Two lines meet (or are parallel) when the reciprocal product d1.m2 + d2.m1 is zero.
                (d1, m1), (d2, m2) = L1, L2
                return dot(d1, m2) + dot(d2, m1) == 0

            L1 = plucker((1, 0, 0), (0, 1, 0))
            L2 = plucker((0, 0, 1), (1, 1, 1))
            L3 = plucker((0, 0, 0), (1, 2, 3))
            L4 = plucker((0, 0, 0), (1, 1, 0))
            print("L1 direction", L1[0], "moment", L1[1])
            print("L2 direction", L2[0], "moment", L2[1])
            print("L1 and L2 meet:", meet(L1, L2))
            print("L1 and L3 meet:", meet(L1, L3))
            print("L1 and L4 meet:", meet(L1, L4))
            print("L2 and L3 meet:", meet(L2, L3))
        """,
    ),

)
