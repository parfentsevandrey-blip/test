package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1960 to 1970: classic algorithms from the decade, each with a short program that prints a checkable result. */
val db7Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 7,
        kind = Kind.EXAMPLE,
        section = "Sorting",
        title = "Quicksort by partitioning",
        originalTitle = "Hoare's quicksort (1961)",
        author = "Tony Hoare; this version keeps the idea and not the in-place partition",
        year = 1961,
        summary = "Hoare's sort: pick a pivot, split the items into those below it, equal to it and above it, and sort the two outer parts in the same way.",
        details = "Hoare published quicksort in 1961, and it is still one of the fastest general-purpose sorts on average. This short version builds new lists rather than swapping items in place, which uses more memory but is easier to read.",
        instructions = """
            1. Save the example as quicksort.py.
            2. Run it: python3 quicksort.py
        """,
        example = """
            def quicksort(items):
                if len(items) <= 1:
                    return items
                pivot = items[len(items) // 2]
                smaller = [x for x in items if x < pivot]
                equal = [x for x in items if x == pivot]
                larger = [x for x in items if x > pivot]
                return quicksort(smaller) + equal + quicksort(larger)

            print(quicksort([31, 4, 15, 9, 26, 5, 58, 9]))
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.EXAMPLE,
        section = "Sorting",
        title = "Heapsort in place",
        originalTitle = "Heapsort (Williams, 1964)",
        author = "J. W. J. Williams",
        year = 1964,
        summary = "A sort that first arranges the items into a heap, then repeatedly takes the largest item off the top and puts it at the end.",
        details = "Williams published heapsort in 1964. It sorts in place with no extra lists, and it takes about n log n steps even in the worst case, which quicksort does not guarantee. The example prints the sorted list.",
        instructions = """
            1. Save the example as heapsort.py.
            2. Run it: python3 heapsort.py
        """,
        example = """
            def heapsort(items):
                a = list(items)
                n = len(a)

                def sift_down(start, end):
                    root = start
                    while True:
                        child = 2 * root + 1
                        if child > end:
                            break
                        if child + 1 <= end and a[child] < a[child + 1]:
                            child += 1
                        if a[root] < a[child]:
                            a[root], a[child] = a[child], a[root]
                            root = child
                        else:
                            break

                for start in range(n // 2 - 1, -1, -1):
                    sift_down(start, n - 1)
                for end in range(n - 1, 0, -1):
                    a[0], a[end] = a[end], a[0]
                    sift_down(0, end - 1)
                return a

            print(heapsort([31, 4, 15, 9, 26, 5, 58, 9]))
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.EXAMPLE,
        section = "Strings",
        title = "Levenshtein distance",
        originalTitle = "Edit distance (Levenshtein, 1965)",
        author = "Vladimir Levenshtein",
        year = 1965,
        summary = "The fewest insertions, deletions and substitutions that turn one word into another, found with a table of prefix distances.",
        details = "Levenshtein described the distance in 1965. The table is filled one row at a time, so only the previous row has to be kept. The example prints three pairs of words and their distances.",
        instructions = """
            1. Save the example as edit_distance.py.
            2. Run it: python3 edit_distance.py
        """,
        example = """
            def levenshtein(a, b):
                # The cheapest way to turn a[:i] into b[:j], one row of the table at a time.
                previous = list(range(len(b) + 1))
                for i, ca in enumerate(a, start=1):
                    current = [i]
                    for j, cb in enumerate(b, start=1):
                        cost = 0 if ca == cb else 1
                        current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost))
                    previous = current
                return previous[-1]

            for a, b in [("kitten", "sitting"), ("flaw", "lawn"), ("", "abc")]:
                print(repr(a), repr(b), levenshtein(a, b))
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.EXAMPLE,
        section = "Strings",
        title = "Knuth–Morris–Pratt string search",
        originalTitle = "Knuth–Morris–Pratt search (Morris and Pratt, 1970)",
        author = "James Morris, Vaughan Pratt and Donald Knuth",
        year = 1970,
        summary = "A search for a pattern in text that never moves backwards in the text, because a table of the pattern's own prefixes says how far to fall back after a mismatch.",
        details = "Morris and Pratt wrote a technical report on the method in 1970, and the full paper by Knuth, Morris and Pratt appeared in 1977. The example finds every place where 'abra' starts in a text.",
        instructions = """
            1. Save the example as kmp.py.
            2. Run it: python3 kmp.py
        """,
        example = """
            def prefix_table(pattern):
                # For each position, the length of the longest proper prefix that is also a suffix.
                table = [0] * len(pattern)
                k = 0
                for i in range(1, len(pattern)):
                    while k > 0 and pattern[i] != pattern[k]:
                        k = table[k - 1]
                    if pattern[i] == pattern[k]:
                        k += 1
                    table[i] = k
                return table

            def search(text, pattern):
                table = prefix_table(pattern)
                found = []
                k = 0
                for i, ch in enumerate(text):
                    while k > 0 and ch != pattern[k]:
                        k = table[k - 1]
                    if ch == pattern[k]:
                        k += 1
                    if k == len(pattern):
                        found.append(i - len(pattern) + 1)
                        k = table[k - 1]
                return found

            print(search("abracadabra abracadabra", "abra"))
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.EXAMPLE,
        section = "Signals",
        title = "Fast Fourier transform by halving",
        originalTitle = "Cooley–Tukey fast Fourier transform (1965)",
        author = "James Cooley and John Tukey",
        year = 1965,
        summary = "The fast Fourier transform: split the samples into even and odd parts, transform each half, and combine them with twiddle factors, which cuts the work from n squared to n log n.",
        details = "Cooley and Tukey published the method in 1965. The example transforms a short signal, [1, 0, -1, 0], and prints the size of each frequency, rounded to six places.",
        instructions = """
            1. Save the example as fft.py.
            2. Run it: python3 fft.py
        """,
        example = """
            import cmath

            def fft(x):
                n = len(x)
                if n == 1:
                    return list(x)
                even = fft(x[0::2])
                odd = fft(x[1::2])
                result = [0] * n
                for k in range(n // 2):
                    twiddle = cmath.exp(-2j * cmath.pi * k / n) * odd[k]
                    result[k] = even[k] + twiddle
                    result[k + n // 2] = even[k] - twiddle
                return result

            spectrum = fft([1, 0, -1, 0])
            print([round(abs(v), 6) for v in spectrum])
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.EXAMPLE,
        section = "Data structures",
        title = "Tries for prefix search",
        originalTitle = "Trie memory (Fredkin, 1960)",
        author = "Edward Fredkin",
        year = 1960,
        summary = "A tree of letters in which every word is a path from the root, so all the words that start with a prefix can be found by walking down to it.",
        details = "Fredkin described the trie in 1960, and the name comes from the word retrieval. The example stores five words and lists the words that begin with each prefix.",
        instructions = """
            1. Save the example as trie.py.
            2. Run it: python3 trie.py
        """,
        example = """
            def make_trie(words):
                root = {}
                for word in words:
                    node = root
                    for letter in word:
                        node = node.setdefault(letter, {})
                    node[""] = True        # an empty key marks the end of a word
                return root

            def words_with_prefix(root, prefix):
                node = root
                for letter in prefix:
                    if letter not in node:
                        return []
                    node = node[letter]
                results = []

                def walk(current, path):
                    if "" in current:
                        results.append(prefix + path)
                    for letter in sorted(k for k in current if k != ""):
                        walk(current[letter], path + letter)

                walk(node, "")
                return results

            trie = make_trie(["tea", "ten", "to", "inn", "tec"])
            print(words_with_prefix(trie, "te"))
            print(words_with_prefix(trie, "to"))
            print(words_with_prefix(trie, "x"))
        """,
    ),
)
