#!/usr/bin/env python3
# Генератор Sources/Lumen/Audio/DTSTables.swift из стандарта DTS (ETSI TS 102 114 V1.6.1, приложение D).
#
#   curl -o spec.pdf https://www.etsi.org/deliver/etsi_ts/102100_102199/102114/01.06.01_60/ts_102114v010601p.pdf
#   pdftotext -layout spec.pdf spec.txt
#   python3 dts-huffman.py spec.txt huff.json          # коды Хаффмана D.5 с проверкой полноты и префиксности
#   curl -o ff_dcadata.c https://raw.githubusercontent.com/FFmpeg/FFmpeg/master/libavcodec/dcadata.c
#   curl -o ff_dcahuff.c https://raw.githubusercontent.com/FFmpeg/FFmpeg/master/libavcodec/dcahuff.c
#   python3 dts-tables.py DTSTables.swift
#
# Из открытой расшифровки берутся только векторные книги D.10 (их нет в тексте стандарта) и значения индексов,
# которые стандарт помечает «invalid»; всё остальное — из стандарта и сверяется с расшифровкой.
import re, json, struct, base64, sys
spec = open('spec.txt').read().split('\n')
def section(title, until):
    s = next(i for i, l in enumerate(spec) if l.startswith(title) and '....' not in l)
    e = next(i for i in range(s + 1, len(spec)) if spec[i].startswith(until) and '....' not in spec[i])
    return [l for l in spec[s+1:e] if 'ETSI' not in l]
def nums(l):
    return [t.replace(',', '.') for t in re.findall(r'-?\d+(?:,\d+)?(?:e-?\d+)?', l)]
def pairs_table(lines, groups, width, n, fallback=None):
    out = {}
    for l in lines:
        t = nums(l)
        if len(t) % width == 0 and 0 < len(t) <= groups * width:
            for g in range(len(t) // width):
                idx = int(t[g*width]); out[idx] = t[g*width+1]
    missing = [i for i in range(n) if i not in out]
    if missing:
        print('  в стандарте нет (invalid) индексов', missing, '— берём из расшифровки')
        for i in missing: out[i] = str(fallback[i])
    assert sorted(out) == list(range(n)), (len(out), n)
    return [out[i] for i in range(n)]
ff = open('ff_dcadata.c').read()
def ffarr(name, typ=int):
    m = re.search(re.escape(name) + r'\)?(?:\[[^\]]*\])+ = \{(.*?)\};', ff, re.S)
    return [typ(x) for x in re.findall(r'-?\d+(?:\.\d+)?(?:e-?\d+)?', m.group(1))]
sf6 = [int(v) for v in pairs_table(section('D.1.1', 'D.1.2'), 2, 3, 64, ffarr('ff_dca_scale_factor_quant6'))]
sf7 = [int(v) for v in pairs_table(section('D.1.2', 'D.2 '), 2, 3, 128, ffarr('ff_dca_scale_factor_quant7'))]
lossy = [int(v) for v in pairs_table(section('D.2.1', 'D.2.2'), 1, 3, 27)]
lossless = [int(v) for v in pairs_table(section('D.2.2', 'D.3 '), 1, 3, 27)]
joint = [float(v) for v in pairs_table(section('D.3 ', 'D.4 '), 4, 2, 129)]
fir = {}
for l in section('D.8 ', 'D.9 '):
    t = nums(l)
    if len(t) == 5 and float(t[0]).is_integer():
        fir[int(t[0])] = [float(x) for x in t[1:]]
assert sorted(fir) == list(range(512)), len(fir)
perfect = [fir[i][0] for i in range(512)]; nonperfect = [fir[i][1] for i in range(512)]
lfe64 = [fir[i][2] for i in range(512)]; lfe128 = [fir[i][3] for i in range(512)]

# Сверка с открытой расшифровкой (ffmpeg) и загрузка таблиц VQ, которых нет в тексте стандарта.
assert ffarr('ff_dca_scale_factor_quant6') == sf6
assert ffarr('ff_dca_scale_factor_quant7') == sf7
assert ffarr('ff_dca_lossy_quant')[:27] == lossy
assert ffarr('ff_dca_lossless_quant')[:27] == lossless
fj = ffarr('ff_dca_joint_scale_factors')
assert all(abs(a / 2**17 - b) < 2e-5 * max(1, b) for a, b in zip(fj, joint)), 'joint'
for name, mine in [('ff_dca_fir_32bands_perfect', perfect), ('ff_dca_fir_32bands_nonperfect', nonperfect)]:
    theirs = ffarr(name, float)
    worst = max(abs(a - b) / max(abs(b), 1e-12) for a, b in zip(mine, theirs))
    print(name, 'max rel diff', worst)
adpcm = ffarr('ff_dca_adpcm_vb'); assert len(adpcm) == 4096 * 4
hfvq = ffarr('ff_dca_high_freq_vq'); assert len(hfvq) == 1024 * 32
assert adpcm[0] == 9928, adpcm[:4]   # первый элемент — пример из D.10.1: 9928 / 2^13 = 1.2119140625

huff = json.load(open('huff.json'))
# Две опечатки в тексте стандарта, восстановлены по полноте кода и открытой расшифровке.
huff['D129'] = [r if not (r[0] == 37 and r[2] == 1581) else [-37, 11, 1581] for r in huff['D129']]
huff['D129'] = [r if not (r[0] == 37 and r[2] == 1582) else [37, 11, 1582] for r in huff['D129']]
huff['D65'] += [[-32, 15, 28848], [32, 15, 28849], [-31, 15, 28850], [31, 15, 28851]]
from fractions import Fraction
for k, rows in huff.items():
    assert sum(Fraction(1, 2**r[1]) for r in rows) == 1, k
    assert len({r[0] for r in rows}) == len(rows), k

def b64(fmt, values):
    return base64.b64encode(struct.pack('<%d%s' % (len(values), fmt), *values)).decode()
def chunks(s, n=120):
    return ',\n'.join('        "' + s[i:i+n] + '"' for i in range(0, len(s), n))
names = sorted(huff)
packed = []
index = {}
for k in names:
    index[k] = (len(packed) // 3, len(huff[k]))
    for lv, ln, cd in huff[k]: packed += [lv, ln, cd]
out = []
out.append('// Создано Lumen/scripts/dts-tables.py из ETSI TS 102 114 V1.6.1 (приложение D). Не править руками.')
out.append('// Векторные кодовые книги D.10 стандарт не публикует в тексте; они взяты из открытой расшифровки')
out.append('// и проверены по примеру из D.10.1. Остальные таблицы сверены с ней же при генерации.')
out.append('import Foundation\n')
out.append('nonisolated enum DTSTables {')
out.append('    static let scale6: [Int32] = [' + ', '.join(map(str, sf6)) + ']')
out.append('    static let scale7: [Int32] = [' + ', '.join(map(str, sf7)) + ']')
out.append('    /// Шаг квантования × 2^22 по ABITS (0…26).')
out.append('    static let stepLossy: [Int32] = [' + ', '.join(map(str, lossy)) + ']')
out.append('    static let stepLossless: [Int32] = [' + ', '.join(map(str, lossless)) + ']')
out.append('    static let jointScale: [Float] = [' + ', '.join(repr(x) for x in joint) + ']')
for nm, arr in [('qmfPerfect', perfect), ('qmfNonPerfect', nonperfect), ('lfe64', lfe64), ('lfe128', lfe128)]:
    out.append(f'    static let {nm}: [Float] = floats(\n' + chunks(b64('f', arr)) + '\n    )')
out.append('    /// Коэффициенты предсказания ADPCM: 4096 векторов по 4 элемента, × 2^13.')
out.append('    static let adpcmVQ: [Int16] = int16s(\n' + chunks(b64('h', adpcm)) + '\n    )')
out.append('    /// Высокочастотные поддиапазоны: 1024 вектора по 32 отсчёта, × 2^4.')
out.append('    static let highFreqVQ: [Int8] = int8s(\n' + chunks(b64('b', hfvq)) + '\n    )')
out.append('    /// Коды Хаффмана: тройки (значение, длина, код) подряд для всех таблиц приложения D.5.')
out.append('    static let huffmanCodes: [Int32] = int32s(\n' + chunks(b64('i', packed)) + '\n    )')
out.append('    /// Таблица → (первая тройка, число строк).')
out.append('    static let huffmanIndex: [String: (Int, Int)] = [' + ', '.join(f'"{k}": ({a}, {b})' for k, (a, b) in index.items()) + ']')
out.append('''
    private static func bytes(_ parts: String...) -> Data { Data(base64Encoded: parts.joined()) ?? Data() }
    private static func floats(_ parts: String...) -> [Float] {
        bytes(parts.joined()).withUnsafeBytes { Array($0.bindMemory(to: Float.self)) }.map { Float(bitPattern: $0.bitPattern.littleEndian) }
    }
    private static func int16s(_ parts: String...) -> [Int16] {
        bytes(parts.joined()).withUnsafeBytes { Array($0.bindMemory(to: Int16.self)) }.map { Int16(littleEndian: $0) }
    }
    private static func int8s(_ parts: String...) -> [Int8] {
        bytes(parts.joined()).withUnsafeBytes { Array($0.bindMemory(to: Int8.self)) }
    }
    private static func int32s(_ parts: String...) -> [Int32] {
        bytes(parts.joined()).withUnsafeBytes { Array($0.bindMemory(to: Int32.self)) }.map { Int32(littleEndian: $0) }
    }
}''')
open(sys.argv[1], 'w').write('\n'.join(out) + '\n')
print('ok', len(packed) // 3, 'huffman rows')
