"""Прогоняет `Lumen --selftest-remux` по тестовым файлам и проверяет отчёты."""
import json
import os
import subprocess
import sys

binary, fixtures, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)

# имя файла → (обязателен ли успех, ожидания)
EXPECT = {
    "h264_aac_ac3_srt.mkv": (True, {"videoCodec": "H.264", "audioTracks": 2, "subtitleTracks": 1, "chapters": 2, "minCues": 2}),
    "hevc10_hdr_eac3.mkv": (True, {"videoCodec": "HEVC", "audioTracks": 1}),
    "live_unknown_size.mkv": (True, {"videoCodec": "H.264", "audioTracks": 1}),
    "h264_mp3_ass.mkv": (True, {"audioTracks": 1, "subtitleTracks": 1, "minCues": 1}),
    "vp9_opus.webm": (True, {"videoCodec": "HEVC", "audioTracks": 0}),
    "av1_aac.mkv": (False, {"videoCodec": "AV1", "audioTracks": 1}),
}

failures = []
for name, (required, expect) in EXPECT.items():
    path = os.path.join(fixtures, name)
    if not os.path.exists(path):
        print(f"– {name}: нет файла, пропуск")
        if required:
            failures.append(name)
        continue
    workdir = os.path.join(out, name + ".out")
    proc = subprocess.run([binary, "--selftest-remux", path, workdir], capture_output=True, text=True, timeout=600)
    start = proc.stdout.find("{")
    report = json.loads(proc.stdout[start:]) if start >= 0 else {"ok": False, "error": proc.stderr[-2000:]}
    with open(os.path.join(out, name + ".json"), "w") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    problems = []
    if not report.get("ok"):
        problems.append(f"ok=false: {report.get('error')}")
    for key, value in expect.items():
        if key == "minCues":
            if report.get("subtitleCues", 0) < value:
                problems.append(f"subtitleCues {report.get('subtitleCues')} < {value}")
        elif report.get(key) != value:
            problems.append(f"{key}={report.get(key)!r}, ожидалось {value!r}")

    mark = "✓" if not problems else ("✗" if required else "⚠")
    print(f"{mark} {name}: {report.get('videoCodec')} {int(report.get('width', 0))}x{int(report.get('height', 0))}, "
          f"{report.get('duration', 0):.1f} с, аудио {report.get('audioTracks')}, субтитры {report.get('subtitleTracks')}"
          f"/{report.get('subtitleCues')}, главы {report.get('chapters')}, "
          f"кадров декодировано {report.get('decodedVideoFrames')}, за {report.get('seconds', 0):.2f} с")
    for note in report.get("notes", []):
        print(f"    · {note}")
    for p in problems:
        print(f"    ! {p}")
    if problems and required:
        failures.append(name)

if failures:
    print("\nПровалено:", ", ".join(failures))
    sys.exit(1)
print("\nВсе обязательные проверки пройдены")
