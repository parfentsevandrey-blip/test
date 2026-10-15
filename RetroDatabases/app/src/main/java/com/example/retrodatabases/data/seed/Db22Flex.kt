package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1820 to 1830: a prime family from Germain, a power series identity of Jacobi and a cube theorem of Ostrogradsky. */
val db22Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 22,
        kind = Kind.FLEX,
        section = "Number theory",
        title = "Sophie Germain primes below 100",
        originalTitle = "Letter to Legendre on the first case of Fermat's last theorem (1823)",
        author = "Sophie Germain",
        year = 1823,
        summary = "A Sophie Germain prime is a prime p for which 2p + 1 is also prime, and the example lists those below 100 by trial division.",
        details = "Germain used such primes in her work on the first case of Fermat's last theorem, which she sent to Legendre in a letter of 1823. The example lists the primes below 100 whose double plus one is also prime, which gives ten numbers, and it checks each one by trial division.",
        instructions = """
            1. Save the example as germain.py.
            2. Run it: python3 germain.py
        """,
        example = """
            def is_prime(n):
                if n < 2:
                    return False
                i = 2
                while i * i <= n:
                    if n % i == 0:
                        return False
                    i += 1
                return True

            # A Sophie Germain prime p has 2p + 1 prime as well.
            germain = [p for p in range(2, 100) if is_prime(p) and is_prime(2 * p + 1)]
            print("Sophie Germain primes below 100:", " ".join(str(p) for p in germain))
            print("count:", len(germain))
        """,
        rare = true,
    ),

    entry(
        databaseId = 22,
        kind = Kind.FLEX,
        section = "Theta functions",
        title = "Jacobi's triple product, the theta series in coefficients",
        originalTitle = "Fundamenta nova theoriae functionum ellipticarum (1829)",
        author = "Carl Gustav Jacob Jacobi",
        year = 1829,
        summary = "Jacobi's triple product writes a power series as an infinite product, and at z = 1 it gives the sum of q to the power n squared, whose coefficients the example checks to degree eight.",
        details = "Jacobi stated the triple product in his 1829 Fundamenta nova of elliptic functions, and the case z = 1 links an infinite product to a sum over the whole numbers n of q to the power n squared. The example multiplies out the product as a truncated power series and compares its coefficients with the sum, which gives 1, 2, 0, 0, 2 and then zeros.",
        instructions = """
            1. Save the example as triple.py.
            2. Run it: python3 triple.py
        """,
        example = """
            DEG = 8

            def mul(a, b):
                # Multiply two power series given as coefficient lists, keeping only the terms up to q to the power DEG.
                out = [0] * (DEG + 1)
                for i, x in enumerate(a):
                    if x == 0:
                        continue
                    for j, y in enumerate(b):
                        if i + j <= DEG:
                            out[i + j] += x * y
                return out

            def single(power, coefficient):
                # The series 1 + coefficient * q to the power, cut off at DEG.
                s = [0] * (DEG + 1)
                s[0] = 1
                if power <= DEG:
                    s[power] += coefficient
                return s

            # Product over n >= 1 of (1 - q^(2n)) times (1 + q^(2n-1)) squared, the case z = 1.
            product = [1] + [0] * DEG
            for n in range(1, DEG + 2):
                if 2 * n <= DEG:
                    product = mul(product, single(2 * n, -1))
                if 2 * n - 1 <= DEG:
                    product = mul(product, single(2 * n - 1, 1))
                    product = mul(product, single(2 * n - 1, 1))

            # Sum of q to the power k squared over the whole numbers k, truncated at DEG.
            theta = [0] * (DEG + 1)
            for k in range(-4, 5):
                if k * k <= DEG:
                    theta[k * k] += 1

            print("product coefficients q^0 to q^8:", " ".join(str(c) for c in product))
            print("theta coefficients q^0 to q^8:", " ".join(str(c) for c in theta))
            print("equal:", product == theta)
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.FLEX,
        section = "Vector calculus",
        title = "Ostrogradsky's divergence theorem on the unit cube",
        originalTitle = "Premiere note sur la theorie de la chaleur (printed 1831)",
        author = "Mikhail Ostrogradsky",
        year = 1826,
        summary = "Ostrogradsky's theorem says the flux of a vector field out of a closed solid equals the integral of its divergence inside, and the example checks it on a cube.",
        details = "Ostrogradsky presented his proof in Paris in February 1826, but the version in print is the St Petersburg note of 1831, and readings in 1827 and 1828 make the dating disputed. Poisson reached the same result in a paper presented in 1828. The example takes the field (x squared, y squared, z squared) on the unit cube and finds the outward flux and the volume integral of the divergence, both exact, and both equal 3.",
        instructions = """
            1. Save the example as ostrogradsky.py.
            2. Run it: python3 ostrogradsky.py
        """,
        example = """
            from fractions import Fraction as F

            # The field F = (x^2, y^2, z^2) on the unit cube. Each face has area 1.
            # On the face at coordinate 1 the outward normal is positive, so F.n is the squared coordinate, 1.
            # On the face at coordinate 0 the outward normal is negative, so F.n is minus the squared coordinate, 0.
            flux = F(0)
            for axis in range(3):
                flux += F(1) ** 2
                flux -= F(0) ** 2
            print("outward flux through the six faces:", flux)

            # The divergence is 2x + 2y + 2z. A monomial x^i y^j z^k integrates over the cube to 1 / ((i + 1)(j + 1)(k + 1)).
            def integrate(i, j, k):
                return F(1, (i + 1) * (j + 1) * (k + 1))

            terms = [(1, 0, 0, 2), (0, 1, 0, 2), (0, 0, 1, 2)]   # (i, j, k, coefficient)
            volume = sum(c * integrate(i, j, k) for i, j, k, c in terms)
            print("volume integral of the divergence:", volume)
            print("theorem holds on the cube:", flux == volume)
        """,
        rare = true,
    ),
)
