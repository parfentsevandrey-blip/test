package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1820 to 1830: small programs that build a table, a life table, a spelling converter and a series test. */
val db22Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 22,
        kind = Kind.PROJECT,
        section = "Calculating engines",
        title = "Method of differences: a table of n squared plus n plus 41",
        originalTitle = "Note on the application of machinery to the computation of astronomical and mathematical tables (1822)",
        author = "Charles Babbage",
        year = 1822,
        summary = "A polynomial table can be built by adding differences only, which is the method that Babbage's engine carried out with gears.",
        details = "If the second differences of a polynomial are constant, each new value needs only two additions, the operation that Babbage's engine performed with its columns of wheels. The example builds the table of n squared plus n plus 41 for n from 0 to 9 by differences alone and checks each entry against the polynomial.",
        instructions = """
            1. Save the example as differences.py.
            2. Run it: python3 differences.py
        """,
        example = """
            # Start with the value at n = 0 and the first difference; the second difference is constant at 2.
            value = 41
            first = 2
            second = 2
            table = [value]
            for n in range(1, 10):
                value = value + first
                first = first + second
                table.append(value)
            print("table for n = 0 to 9:", " ".join(str(v) for v in table))
            print("matches n squared plus n plus 41:", all(table[n] == n * n + n + 41 for n in range(10)))
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.PROJECT,
        section = "Life tables",
        title = "Gompertz life table with a doubling death rate",
        originalTitle = "On the nature of the function expressive of the law of human mortality (1825)",
        author = "Benjamin Gompertz",
        year = 1825,
        summary = "Gompertz's law says the death rate rises by a fixed factor from one age to the next, so a life table can be built by multiplying survivors by one minus the rate at each step.",
        details = "Gompertz proposed in 1825 that the force of mortality grows geometrically with age. The example uses a simplified version in which the chance of dying doubles each decade, starts from a cohort of 1000 and builds the survivors at each decade with exact fractions.",
        instructions = """
            1. Save the example as gompertz.py.
            2. Run it: python3 gompertz.py
        """,
        example = """
            from fractions import Fraction as F

            survivors = F(1000)
            rate = F(1, 100)   # chance of dying in the first decade
            print("age 0: 1000.00")
            for step in range(1, 5):
                survivors = survivors * (1 - rate)
                print(f"age {step * 10}: {float(survivors):.2f}")
                rate = rate * 2   # the death rate doubles each decade
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.PROJECT,
        section = "Spelling",
        title = "Spelling converter for Webster's American spellings",
        originalTitle = "An American Dictionary of the English Language (1828)",
        author = "Noah Webster",
        year = 1828,
        summary = "Webster's 1828 spellings replaced British endings such as colour, centre and honour with color, center and honor, and a small program makes the same swaps on a word list.",
        details = "Webster's dictionary of 1828 adopted American spellings for words with -our, -re and similar endings, and the example applies those changes as plain text rules. The rules are crude, so the word list avoids words such as four that the rules would wrongly change, and the program prints each result to be checked by eye.",
        instructions = """
            1. Save the example as spelling.py.
            2. Run it: python3 spelling.py
        """,
        example = """
            # Ending rules in the spirit of Webster's 1828 spellings; the order of the rules matters.
            rules = [("our", "or"), ("tre", "ter"), ("re", "er")]
            words = ["colour", "honour", "centre", "theatre", "labour", "fibre", "rumour", "metre"]

            def convert(word):
                for old, new in rules:
                    if word.endswith(old):
                        return word[: -len(old)] + new
                return word

            for word in words:
                print(f"{word} becomes {convert(word)}")
            print("converted words:", sum(1 for w in words if convert(w) != w))
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.PROJECT,
        section = "Series",
        title = "Cauchy's condensation test, partial sums in exact fractions",
        originalTitle = "Cours d'analyse de l'Ecole royale polytechnique (1821)",
        author = "Augustin-Louis Cauchy",
        year = 1821,
        summary = "Cauchy's condensation test says a decreasing series converges exactly when the series of 2 to the k times its term at 2 to the k converges, and the example tabulates both sums.",
        details = "The condensation test appears in Cauchy's 1821 course on analysis, and it turns a slowly decreasing series into one with far fewer terms. The example takes the terms 1 over n squared and 1 over n, forms the condensed series, and prints its exact partial sums, which level off for the first and grow without bound for the second.",
        instructions = """
            1. Save the example as condensation.py.
            2. Run it: python3 condensation.py
        """,
        example = """
            from fractions import Fraction as F

            # For the series of 1 / n^p, the condensed terms are 2^k times the term at n = 2^k.
            def condensed_terms(power, count):
                return [F(2) ** k * (F(2) ** k) ** (-power) for k in range(count)]

            for power in (2, 1):
                total = F(0)
                sums = []
                for t in condensed_terms(power, 5):
                    total += t
                    sums.append(str(total))
                print(f"p={power} partial sums of condensed series: " + " ".join(sums))
        """,
    ),
)
