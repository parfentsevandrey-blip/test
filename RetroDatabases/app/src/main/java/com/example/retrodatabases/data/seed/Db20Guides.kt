package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1840 to 1850: Sylvester's resultant, Jacobi's rotation, Cauchy's descent, Stokes's jump and Bienayme's branching. */
val db20Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 20,
        kind = Kind.GUIDE,
        section = "Elimination",
        title = "How Sylvester's matrix gives the resultant of two polynomials",
        originalTitle = "A method of determining by mere inspection the derivatives from two equations of any degree",
        author = "James Joseph Sylvester",
        year = 1840,
        summary = "The resultant of two polynomials is the determinant of a matrix built from their coefficients, and it is zero exactly when they share a root.",
        details = "Sylvester described this elimination in 1840 in the Philosophical Magazine, and the matrix is named after him. The example builds the 4 by 4 matrix for x squared minus 3x plus 2 and x squared plus x minus 1, computes its determinant in exact fractions, and checks it against the product of the second polynomial over the roots of the first.",
        instructions = """
            1. Save the example as sylvester.py.
            2. Run it: python3 sylvester.py
        """,
        example = """
            from fractions import Fraction

            def sylvester_matrix(f, g):
                # f and g list their coefficients from the highest power down
                m, n = len(f) - 1, len(g) - 1
                size = m + n
                rows = []
                for i in range(n):
                    rows.append([0] * i + f + [0] * (size - m - 1 - i))
                for i in range(m):
                    rows.append([0] * i + g + [0] * (size - n - 1 - i))
                return rows

            def determinant(matrix):
                # exact Gaussian elimination with fractions
                a = [[Fraction(x) for x in row] for row in matrix]
                size = len(a)
                det = Fraction(1)
                for col in range(size):
                    pivot = next(r for r in range(col, size) if a[r][col] != 0)
                    if pivot != col:
                        a[col], a[pivot] = a[pivot], a[col]
                        det = -det
                    det *= a[col][col]
                    for r in range(col + 1, size):
                        factor = a[r][col] / a[col][col]
                        for c in range(col, size):
                            a[r][c] -= factor * a[col][c]
                return det

            def evaluate(poly, x):
                value = 0
                for coefficient in poly:
                    value = value * x + coefficient
                return value

            f = [1, -3, 2]    # x squared minus 3x plus 2, with roots 1 and 2
            g = [1, 1, -1]    # x squared plus x minus 1
            M = sylvester_matrix(f, g)
            for row in M:
                print(row)
            res = determinant(M)
            print("determinant:", res)
            roots = [1, 2]
            for r in roots:
                assert evaluate(f, r) == 0
            values = [evaluate(g, r) for r in roots]
            print("g at the roots of f:", values)
            product = 1
            for v in values:
                product *= v
            print("product:", product)
            print("resultant equals product:", res == product)
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.GUIDE,
        section = "Eigenvalues",
        title = "How one Jacobi rotation diagonalises a symmetric 2 by 2 matrix",
        originalTitle = "Ueber ein leichtes Verfahren, die in der Theorie der Saecularstoerungen vorkommenden Gleichungen numerisch aufzuloesen",
        author = "Carl Gustav Jacob Jacobi",
        year = 1846,
        summary = "A rotation chosen to clear the off-diagonal entry of a symmetric matrix leaves the eigenvalues on the diagonal.",
        details = "Jacobi published the method in 1846 in Crelle's Journal, for the equations of planetary secular perturbations. The example uses a single rotation with cosine 3/5 and sine 4/5, so every entry stays an exact fraction.",
        instructions = """
            1. Save the example as jacobi_rotation.py.
            2. Run it: python3 jacobi_rotation.py
        """,
        example = """
            from fractions import Fraction as F

            # Symmetric matrix A, and the rotation with cosine 3/5 and sine 4/5 (a 3 4 5 triangle)
            A = [[F(9), F(-12)], [F(-12), F(2)]]
            c, s = F(3, 5), F(4, 5)
            R = [[c, -s], [s, c]]

            def transpose(M):
                return [[M[j][i] for j in range(2)] for i in range(2)]

            def multiply(X, Y):
                return [[sum(X[i][k] * Y[k][j] for k in range(2)) for j in range(2)] for i in range(2)]

            B = multiply(transpose(R), multiply(A, R))
            print("rotated matrix:", [[str(x) for x in row] for row in B])
            print("off-diagonal entry:", B[0][1])
            print("eigenvalues on the diagonal:", B[0][0], B[1][1])
            print("trace check:", A[0][0] + A[1][1] == B[0][0] + B[1][1])
            print("determinant check:", A[0][0] * A[1][1] - A[0][1] * A[1][0] == B[0][0] * B[1][1])
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.GUIDE,
        section = "Simultaneous equations",
        title = "Cauchy's steepest descent for solving equations",
        originalTitle = "Methode generale pour la resolution des systemes d'equations simultanees",
        author = "Augustin Louis Cauchy",
        year = 1847,
        summary = "Cauchy solved simultaneous equations by moving downhill on a function whose lowest point is the solution, choosing each step to reach the lowest point along the line.",
        details = "Cauchy proposed the method in 1847 in the Comptes rendus. The example applies it to a quadratic bowl with its minimum at (1, 1/2) and prints three steps as exact fractions, each with step length 2/3.",
        instructions = """
            1. Save the example as cauchy_descent.py.
            2. Run it: python3 cauchy_descent.py
        """,
        example = """
            from fractions import Fraction as F

            # f(x, y) = x*x/2 + y*y - x - y has Hessian diag(1, 2) and its minimum at (1, 1/2)
            def gradient(x, y):
                return (x - 1, 2 * y - 1)

            x, y = F(0), F(0)
            for step in range(3):
                gx, gy = gradient(x, y)
                gg = gx * gx + gy * gy              # squared length of the gradient
                ggH = gx * gx + 2 * gy * gy         # gradient times Hessian times gradient
                t = gg / ggH                        # exact step length along the negative gradient
                x, y = x - t * gx, y - t * gy
                print(f"step {step}: gradient ({gx}, {gy}), step {t}, next point ({x}, {y})")
            gx, gy = gradient(x, y)
            print(f"gradient after three steps: ({gx}, {gy})")
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.GUIDE,
        section = "Series of functions",
        title = "A series of continuous terms with a jump in its sum",
        originalTitle = "On the critical values of the sums of periodic series",
        author = "George Gabriel Stokes",
        year = 1847,
        summary = "A sum of continuous functions can be discontinuous when the convergence is not uniform, a point that Stokes reached in 1847.",
        details = "Stokes published the result in 1847 in the Cambridge Transactions. The example sums the telescoping terms x to the n minus x to the n plus one, which give partial sums that tend to 1 for x below 1 but stay at 0 at x equal to 1.",
        instructions = """
            1. Save the example as stokes_series.py.
            2. Run it: python3 stokes_series.py
        """,
        example = """
            from fractions import Fraction as F

            # The terms x**n - x**(n+1) are continuous; their partial sums telescope to 1 - x**(N+1).
            def partial_sum(x, N):
                return sum(x**n - x**(n + 1) for n in range(N + 1))

            for N in range(4):
                print("N =", N, "partial sum at 9/10:", partial_sum(F(9, 10), N))
            for N in range(4):
                print("N =", N, "partial sum at 1:", partial_sum(F(1), N))
            print("sum at 9/10 tends to 1, but the sum at 1 is 0 for every N")
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.GUIDE,
        section = "Branching",
        title = "How Bienayme's branching count gives the chance a family dies out",
        originalTitle = "De la loi de multiplication et de la duree des familles",
        author = "Irenee Jules Bienayme",
        year = 1845,
        summary = "Each individual leaves a random number of children, and the chance that the line dies out is the smallest solution of q equal to f(q) for the generating function f.",
        details = "Bienayme studied this branching model in 1845, and Galton and Watson asked the same question in the 1870s. The example uses offspring probabilities 1/4, 1/4 and 1/2 for zero, one and two children, iterates the generating function from zero, and checks that 1/2 is a fixed point.",
        rare = true,
        instructions = """
            1. Save the example as branching.py.
            2. Run it: python3 branching.py
        """,
        example = """
            from fractions import Fraction as F

            # Offspring probabilities: 0 children with 1/4, 1 child with 1/4, 2 children with 1/2.
            probs = [F(1, 4), F(1, 4), F(1, 2)]

            def generating(s):
                return sum(p * s**k for k, p in enumerate(probs))

            mean = sum(k * p for k, p in enumerate(probs))
            print("mean number of children:", mean)
            q = F(0)
            for n in range(1, 4):
                q = generating(q)
                print(f"chance of extinction by generation {n}: {q}")
            print("fixed point check at 1/2:", generating(F(1, 2)) == F(1, 2))
        """,
    ),
)
