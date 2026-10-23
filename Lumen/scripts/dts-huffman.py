import re, json, sys
from fractions import Fraction
lines = open(sys.argv[1]).read().split('\n')
start = next(i for i,l in enumerate(lines) if l.startswith('D.5     Huffman Code Books'))
end = next(i for i,l in enumerate(lines) if l.startswith('D.6     Block Code Books'))
num = re.compile(r'-?\d{1,3}(?: \d{3})+(?!\d)|-?\d+')
hdr = re.compile(r'Table\s+(S?[A-G]\d+)')
tables = {}
cur = []  # [(name, x)]
pending_hdr = False
for i in range(start, end):
    l = lines[i]
    if 'ETSI' in l or l.strip().startswith('D.5'):
        continue
    hs = [(m.group(1), m.start()) for m in hdr.finditer(l)]
    if hs:
        if not pending_hdr: cur = []
        cur += hs
        pending_hdr = True
        continue
    toks = [(m.group().replace(' ', ''), m.start()) for m in num.finditer(l)]
    if not toks or any(c.isalpha() for c in re.sub(r'(Quantization|level|Level|Code|length|ABITS|Cod|e)', '', l)):
        if toks == []: pass
        continue
    if len(toks) % 3 != 0:
        print('skip line', i+1, repr(l), file=sys.stderr); continue
    pending_hdr = False
    trip = [toks[k:k+3] for k in range(0, len(toks), 3)]
    for t in trip:
        x = t[0][1]
        if len(set(n for n,_ in cur)) == 1:
            name = cur[0][0]
        else:
            name = min(cur, key=lambda h: abs(h[1] - x))[0]
        tables.setdefault(name, []).append(tuple(int(v) for v,_ in t))
ok = True
for name, rows in sorted(tables.items()):
    k = Fraction(0)
    for lv, ln, cd in rows:
        k += Fraction(1, 2**ln)
        if cd >= 2**ln: print('BAD code range', name, lv, ln, cd); ok = False
    codes = [(format(cd, '0%db' % ln)) for lv, ln, cd in rows]
    pf = all(not b.startswith(a) for a in codes for b in codes if a is not b)
    levels = sorted(r[0] for r in rows)
    n = int(re.sub(r'\D', '', name))
    if name.endswith('12') and n == 12: exp = list(range(1, 13))
    elif n == 4: exp = list(range(0, 4))
    else: exp = list(range(-(n//2), n//2 + 1))
    good = k == 1 and pf and levels == exp
    if not good: ok = False
    print(f'{name:6} rows={len(rows):3} kraft={float(k):.6f} prefixfree={pf} levels_ok={levels == exp}')
json.dump(tables, open(sys.argv[2], 'w'))
print('ALL OK' if ok else 'PROBLEMS')
