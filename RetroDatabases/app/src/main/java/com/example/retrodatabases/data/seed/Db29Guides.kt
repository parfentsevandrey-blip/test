package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1750 to 1760: determinants, averaging observations, nine points on a cubic, a comet's period and a lunar distance. */
val db29Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 29,
        kind = Kind.GUIDE,
        section = "Linear equations",
        title = "Cramer's rule for linear equations",
        originalTitle = "Introduction a l'analyse des lignes courbes algebriques",
        author = "Gabriel Cramer",
        year = 1750,
        summary = "A system of linear equations is solved by dividing determinants: each unknown is the determinant with one column replaced by the right-hand side, over the determinant of the matrix.",
        details = "Cramer gave the rule in his treatise of 1750 on algebraic curves, in the part on solving equations. The example solves a three-by-three system with determinants and checks the answer by substitution.",
        instructions = """
            1. Save the example as cramer.py.
            2. Run it: python3 cramer.py
        """,
        example = """
            from fractions import Fraction as F

            A = [[2, 1, -1], [1, -1, 2], [3, 2, 1]]
            b = [1, 5, 10]


            def det(m):
                return (m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                        - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                        + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]))


            def with_column(m, col, values):
                return [[values[r] if c == col else m[r][c] for c in range(3)] for r in range(3)]


            d = det(A)
            print("determinant:", d)
            solution = [F(det(with_column(A, c, b)), d) for c in range(3)]
            for name, value in zip("xyz", solution):
                print(name, "=", value)
            residuals_ok = all(sum(A[r][c] * solution[c] for c in range(3)) == b[r] for r in range(3))
            print("residuals zero:", residuals_ok)
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.GUIDE,
        section = "Observations",
        title = "Mayer's method of averages for too many equations",
        originalTitle = "Mayer's 1750 work on the libration of the Moon",
        author = "Tobias Mayer",
        year = 1750,
        summary = "When there are more equations than unknowns, the equations are grouped and each group is averaged, which gives a line through the group means.",
        details = "Mayer's work of 1750 used 27 observations of a crater on the Moon, which he pooled into groups. Accounts differ on whether his system was linear, and the example fits a straight line to six observations in two groups.",
        instructions = """
            1. Save the example as mayer_averages.py.
            2. Run it: python3 mayer_averages.py
        """,
        example = """
            from fractions import Fraction as F

            data = [(0, 1), (1, 3), (2, 4), (3, 6), (4, 8), (5, 9)]
            groups = [data[:3], data[3:]]
            means = []
            for group in groups:
                mean_x = F(sum(x for x, _ in group), len(group))
                mean_y = F(sum(y for _, y in group), len(group))
                means.append((mean_x, mean_y))
            (x1, y1), (x2, y2) = means
            slope = (y2 - y1) / (x2 - x1)
            intercept = y1 - slope * x1
            print("group means:", [(str(x), str(y)) for x, y in means])
            print("slope:", slope)
            print("intercept:", intercept)
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.GUIDE,
        section = "Curves",
        title = "How nine points fix a cubic curve, Cramer's count of 1750",
        originalTitle = "Introduction a l'analyse des lignes courbes algebriques",
        author = "Gabriel Cramer",
        year = 1750,
        summary = "Nine points usually fix a cubic, but these nine points on y = x cubed minus x lie on two cubics, so the solution is a pencil rather than one curve.",
        details = "Cramer's count gives a cubic ten coefficients, so nine points usually fix it up to a common factor. The example writes the nine conditions as a nine-by-ten matrix, finds its rank by elimination, and checks two cubics that pass through all nine points.",
        rare = true,
        instructions = """
            1. Save the example as nine_points.py.
            2. Run it: python3 nine_points.py
        """,
        example = """
            from fractions import Fraction as F

            points = [(x, x**3 - x) for x in range(-4, 5)]


            def monomials(x, y):
                # the ten monomials of a cubic in x and y, in this order
                return [x**3, x**2 * y, x * y**2, y**3, x**2, x * y, y**2, x, y, 1]


            def pivot_columns(rows):
                m = [row[:] for row in rows]
                pivots = []
                current = 0
                for col in range(len(m[0])):
                    pivot = next((r for r in range(current, len(m)) if m[r][col] != 0), None)
                    if pivot is None:
                        continue
                    m[current], m[pivot] = m[pivot], m[current]
                    p = m[current][col]
                    m[current] = [v / p for v in m[current]]
                    for r in range(len(m)):
                        if r != current and m[r][col] != 0:
                            factor = m[r][col]
                            m[r] = [a - factor * b for a, b in zip(m[r], m[current])]
                    pivots.append(col)
                    current += 1
                    if current == len(m):
                        break
                return pivots


            rows = [[F(v) for v in monomials(x, y)] for x, y in points]
            pivots = pivot_columns(rows)
            print("points:", len(points), "unknowns:", 10)
            print("rank:", len(pivots))
            print("solution space dimension:", 10 - len(pivots))

            F1 = [-1, 0, 0, 0, 0, 0, 0, 1, 1, 0]
            F2 = [-576, 216, -27, 1, 0, 0, 0, 576, 0, 0]


            def vanishes(coeffs):
                return all(sum(c * v for c, v in zip(coeffs, monomials(x, y))) == 0 for x, y in points)


            print("F1 vanishes at all nine points:", vanishes(F1))
            print("F2 vanishes at all nine points:", vanishes(F2))
            print("F1 and F2 are not proportional:", any(F1[i] * F2[j] != F1[j] * F2[i] for i in range(10) for j in range(10)))
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.GUIDE,
        section = "Comets",
        title = "How Halley's 76-year period gives the return years",
        originalTitle = "Halley's comet period, as applied in 1758",
        author = "Edmond Halley",
        year = 1758,
        summary = "Adding the 76-year period to the 1682 apparition gives the return years 1758, 1834 and 1910, with no allowance for the pull of the planets.",
        details = "Halley linked the comets of 1531, 1607 and 1682 and expected the next return around 1758. This simple period method is a neighbour of the Clairaut record, which is a different method that corrects for the planets' pull.",
        instructions = """
            1. Save the example as halley_period.py.
            2. Run it: python3 halley_period.py
        """,
        example = """
            PERIOD = 76
            FIRST_APPARITION = 1682
            for k in range(4):
                print(FIRST_APPARITION + PERIOD * k)
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.GUIDE,
        section = "Navigation",
        title = "How the spherical cosine rule gives a lunar distance",
        originalTitle = "Spherical trigonometry for lunar distances",
        author = "Navigators of the 1750s",
        year = 1753,
        summary = "The angle between the Moon and a star follows from the spherical cosine rule, which is the calculation behind lunar distance navigation.",
        details = "Navigators measured the lunar distance with a sextant and worked out their longitude from the Moon's place in Mayer's tables of 1753. The example uses exact sines and cosines of two stylised positions, which give the cosine of the distance as 63/65.",
        instructions = """
            1. Save the example as lunar_distance.py.
            2. Run it: python3 lunar_distance.py
        """,
        example = """
            from fractions import Fraction as F

            # Two stylised sky positions with exact sines and cosines: a 3-4-5 triangle and a 5-12-13 triangle.
            sin_a, cos_a = F(3, 5), F(4, 5)
            sin_b, cos_b = F(5, 13), F(12, 13)
            cos_delta = 1   # the two bodies have the same longitude difference, so its cosine is 1
            cos_d = sin_a * sin_b + cos_a * cos_b * cos_delta
            print("cosine of the lunar distance:", cos_d)
            print("sine and cosine pairs check:", sin_a**2 + cos_a**2 == 1 and sin_b**2 + cos_b**2 == 1)
        """,
    ),

)
