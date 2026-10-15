package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1970 to 1980: classic algorithms of the decade, each with a short program that prints a checkable result. */
val db8Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 8,
        kind = Kind.EXAMPLE,
        section = "Strings",
        title = "Boyer–Moore string search",
        originalTitle = "A fast string searching algorithm (Boyer and Moore, 1977)",
        author = "Robert Boyer and J Strother Moore",
        year = 1977,
        summary = "A string search that compares from the end of the pattern and skips ahead with a bad-character table, so many characters of the text are never looked at.",
        details = "Boyer and Moore published the method in 1977. The example uses the bad-character rule only, and prints the positions where the pattern occurs, including overlapping matches.",
        instructions = """
            1. Save the example as boyer_moore.py.
            2. Run it: python3 boyer_moore.py
        """,
        example = """
            def boyer_moore(text, pattern):
                # Bad-character table: the last position of each letter in the pattern.
                last = {ch: i for i, ch in enumerate(pattern)}
                found = []
                shift = 0
                while shift <= len(text) - len(pattern):
                    j = len(pattern) - 1
                    while j >= 0 and pattern[j] == text[shift + j]:
                        j -= 1
                    if j < 0:
                        found.append(shift)
                        shift += 1
                    else:
                        shift += max(1, j - last.get(text[shift + j], -1))
                return found

            print(boyer_moore("here is a simple example", "example"))
            print(boyer_moore("abababa", "aba"))
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.EXAMPLE,
        section = "Strings",
        title = "Aho–Corasick multiple search",
        originalTitle = "Efficient string matching: an aid to bibliographic search (1975)",
        author = "Alfred Aho and Margaret Corasick",
        year = 1975,
        summary = "A search for many patterns at once: a trie of the patterns with failure links, so the text is read in one pass whatever the number of patterns.",
        details = "Aho and Corasick published the method in 1975, for searching a bibliography for a set of keywords. The example finds four patterns in one word and prints where each one starts.",
        instructions = """
            1. Save the example as aho_corasick.py.
            2. Run it: python3 aho_corasick.py
        """,
        example = """
            from collections import deque

            def build(patterns):
                goto = [{}]
                out = [set()]
                for p in patterns:
                    node = 0
                    for ch in p:
                        if ch not in goto[node]:
                            goto.append({})
                            out.append(set())
                            goto[node][ch] = len(goto) - 1
                        node = goto[node][ch]
                    out[node].add(p)
                fail = [0] * len(goto)
                queue = deque()
                for child in goto[0].values():
                    queue.append(child)
                while queue:
                    node = queue.popleft()
                    for ch, child in goto[node].items():
                        queue.append(child)
                        f = fail[node]
                        while f and ch not in goto[f]:
                            f = fail[f]
                        fail[child] = goto[f].get(ch, 0)
                        out[child] |= out[fail[child]]
                return goto, fail, out

            def search(text, patterns):
                goto, fail, out = build(patterns)
                matches = []
                node = 0
                for i, ch in enumerate(text):
                    while node and ch not in goto[node]:
                        node = fail[node]
                    node = goto[node].get(ch, 0)
                    for p in out[node]:
                        matches.append((i - len(p) + 1, p))
                return sorted(matches)

            print(search("ushers", ["he", "she", "his", "hers"]))
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Pollard's rho factoring",
        originalTitle = "A Monte Carlo method for factorization (Pollard, 1975)",
        author = "John Pollard",
        year = 1975,
        summary = "A factoring method that runs a pseudo-random sequence modulo the number, and uses a greatest common divisor to catch a factor when two runners meet.",
        details = "Pollard published the method in 1975. It finds small factors quickly and needs no table of primes. The example factors two numbers, each the product of two primes.",
        instructions = """
            1. Save the example as pollard.py.
            2. Run it: python3 pollard.py
        """,
        example = """
            from math import gcd

            def pollard_rho(n):
                # Two runners step through x -> x*x + c modulo n; their difference shares a factor with n when they meet.
                if n % 2 == 0:
                    return 2
                c = 1
                while True:
                    x = y = 2
                    d = 1
                    while d == 1:
                        x = (x * x + c) % n
                        y = (y * y + c) % n
                        y = (y * y + c) % n
                        d = gcd(abs(x - y), n)
                    if d != n:
                        return d
                    c += 1

            for n in [8051, 10403]:
                f = pollard_rho(n)
                print(n, "=", min(f, n // f), "*", max(f, n // f))
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Miller–Rabin primality test",
        originalTitle = "Miller–Rabin test (Miller, 1976; Rabin, 1980)",
        author = "Gary Miller and Michael Rabin",
        year = 1976,
        summary = "A test that checks whether a number behaves like a prime under powers of several bases; with the fixed bases used here it is reliable for every number in a large range.",
        details = "Miller described the test in 1976, and Rabin's randomised version followed in 1980. The example uses six fixed bases, which are enough for every number up to about 3.47 times ten to the twelfth.",
        instructions = """
            1. Save the example as miller_rabin.py.
            2. Run it: python3 miller_rabin.py
        """,
        example = """
            def is_prime(n):
                if n < 2:
                    return False
                for p in [2, 3, 5, 7, 11, 13]:
                    if n % p == 0:
                        return n == p
                d, s = n - 1, 0
                while d % 2 == 0:
                    d //= 2
                    s += 1
                for a in [2, 3, 5, 7, 11, 13]:
                    x = pow(a, d, n)
                    if x in (1, n - 1):
                        continue
                    for _ in range(s - 1):
                        x = x * x % n
                        if x == n - 1:
                            break
                    else:
                        return False
                return True

            print([n for n in range(2, 50) if is_prime(n)])
            print(is_prime(1000003), is_prime(1000001))
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.EXAMPLE,
        section = "Graphs",
        title = "Tarjan's strongly connected components",
        originalTitle = "Depth-first search and linear graph algorithms (Tarjan, 1972)",
        author = "Robert Tarjan",
        year = 1972,
        summary = "Finds the strongly connected components of a directed graph in one depth-first search, using a stack and low-link numbers.",
        details = "Tarjan published the algorithm in 1972. A component is complete when a node's low-link number equals its own index, and the nodes above it on the stack form the component. The example finds three components in a small graph.",
        instructions = """
            1. Save the example as tarjan.py.
            2. Run it: python3 tarjan.py
        """,
        example = """
            def tarjan(graph):
                index, low = {}, {}
                stack, on_stack = [], set()
                result = []
                counter = [0]

                def visit(v):
                    index[v] = low[v] = counter[0]
                    counter[0] += 1
                    stack.append(v)
                    on_stack.add(v)
                    for w in graph.get(v, []):
                        if w not in index:
                            visit(w)
                            low[v] = min(low[v], low[w])
                        elif w in on_stack:
                            low[v] = min(low[v], index[w])
                    if low[v] == index[v]:
                        component = []
                        while True:
                            w = stack.pop()
                            on_stack.discard(w)
                            component.append(w)
                            if w == v:
                                break
                        result.append(sorted(component))

                for v in sorted(graph):
                    if v not in index:
                        visit(v)
                return sorted(result)

            graph = {1: [2], 2: [3], 3: [1, 4], 4: [5], 5: [6], 6: [4], 7: [6]}
            print(tarjan(graph))
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.EXAMPLE,
        section = "Compression",
        title = "LZ78 compression",
        originalTitle = "Compression of individual sequences via variable-rate coding (Ziv and Lempel, 1978)",
        author = "Jacob Ziv and Abraham Lempel",
        year = 1978,
        summary = "A compression method that builds a dictionary of phrases as it reads, and writes each new phrase as the index of a known phrase plus one character.",
        details = "Ziv and Lempel published the method in 1978. The decoder rebuilds the same dictionary from the tokens alone, so no table is sent with the data. The example encodes a repeated text and decodes it back.",
        instructions = """
            1. Save the example as lz78.py.
            2. Run it: python3 lz78.py
        """,
        example = """
            def lz78_encode(text):
                dictionary = {"": 0}
                output = []
                current = ""
                for ch in text:
                    if current + ch in dictionary:
                        current += ch
                    else:
                        output.append((dictionary[current], ch))
                        dictionary[current + ch] = len(dictionary)
                        current = ""
                if current:
                    output.append((dictionary[current], ""))
                return output

            def lz78_decode(tokens):
                entries = [""]
                pieces = []
                for index, ch in tokens:
                    phrase = entries[index] + ch
                    entries.append(phrase)
                    pieces.append(phrase)
                return "".join(pieces)

            tokens = lz78_encode("abababababab")
            print(tokens)
            print(lz78_decode(tokens) == "abababababab")
        """,
    ),
)
