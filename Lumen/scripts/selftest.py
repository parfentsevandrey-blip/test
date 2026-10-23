"""Прогоняет `Lumen --selftest-playback` по тестовым файлам и проверяет отчёты движка."""
import json
import os
import subprocess
import sys

binary, fixtures, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)

# имя файла → (обязателен ли успех, ожидания)
EXPECT = {
    "h264_aac_ac3_srt.mkv": (True, {"engine": "matroska", "videoCodec": "H.264", "playableAudio": 2, "chapters": 2, "subtitleText": 1}),
    "h264_aac_dts.mkv": (True, {"engine": "matroska", "playableAudio": 2, "defaultAudioContains": "DTS"}),
    "h264_flac_pcm.mkv": (True, {"engine": "matroska", "playableAudio": 2}),
    "h264_pgs.mkv": (True, {"engine": "matroska", "subtitleBitmap": 1}),
    "hevc10_hdr_eac3.mkv": (True, {"engine": "matroska", "videoCodec": "HEVC", "playableAudio": 1}),
    "live_unknown_size.mkv": (True, {"engine": "matroska", "playableAudio": 1}),
    "h264_mp3_ass.mkv": (True, {"engine": "matroska", "playableAudio": 1, "subtitleText": 1}),
    "vp9_opus.webm": (True, {"engine": "matroska", "videoCodec": "VP9", "playableAudio": 1}),
    "h264_aac_text.mp4": (True, {"engine": "avplayer", "playableAudio": 1}),
    "av1_aac.mkv": (False, {"engine": "matroska", "videoCodec": "AV1"}),
}

failures = []
for name, (required, expect) in EXPECT.items():
    path = os.path.join(fixtures, name)
    if not os.path.exists(path):
        print(f"– {name}: нет файла, пропуск")
        if required:
            failures.append(name)
        continue
    try:
        proc = subprocess.run([binary, "--selftest-playback", path], capture_output=True, text=True, timeout=120)
        start = proc.stdout.find("{")
        report = json.loads(proc.stdout[start:]) if start >= 0 else {"ok": False, "problems": ["нет отчёта: " + proc.stderr[-1500:]]}
    except subprocess.TimeoutExpired:
        report = {"ok": False, "problems": ["зависание (таймаут 120 с)"]}
    with open(os.path.join(out, name + ".json"), "w") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    problems = list(report.get("problems", []))
    audio = report.get("audio", [])
    playable = sum(1 for a in audio if a.get("available"))
    seen = report.get("subtitleSeen", {})
    for key, value in expect.items():
        if key == "playableAudio":
            if playable < value:
                problems.append(f"воспроизводимых дорожек {playable}, ожидалось {value}")
        elif key == "defaultAudioContains":
            index = report.get("selectedAudio", -1)
            detail = audio[index]["detail"] if 0 <= index < len(audio) else ""
            if value not in detail:
                problems.append(f"звук по умолчанию «{detail}», ожидался {value}")
        elif key == "subtitleText":
            texts = [v for v in seen.values() if v and not v.startswith("bitmap")]
            if len(texts) < value:
                problems.append(f"текстовые субтитры не показались: {seen}")
        elif key == "subtitleBitmap":
            bitmaps = [v for v in seen.values() if v.startswith("bitmap")]
            if len(bitmaps) < value:
                problems.append(f"субтитры-картинки не показались: {seen}")
        elif key == "videoCodec":
            if value not in (report.get(key) or ""):
                problems.append(f"{key}={report.get(key)!r}, ожидалось {value!r}")
        elif report.get(key) != value:
            problems.append(f"{key}={report.get(key)!r}, ожидалось {value!r}")

    mark = "✓" if not problems else ("✗" if required else "⚠")
    size = report.get("size", [0, 0])
    print(f"{mark} {name}: {report.get('engine')} · {report.get('videoCodec')} {int(size[0])}x{int(size[1])}, "
          f"{report.get('duration', 0):.1f} с, открыт за {report.get('openSeconds', 0) * 1000:.0f} мс, "
          f"сыграно {report.get('playedSeconds', 0):.2f} с, перемотка → {report.get('seekLanded', 0):.2f} с")
    for a in audio:
        print(f"    звук: {a['title']} — {a['detail']}{'' if a['available'] else ' (недоступен)'}")
    if report.get("audioSwitch"):
        print(f"    переключение звука: часы прошли {', '.join(f'{x:.2f}' for x in report['audioSwitch'])} с")
    for title, value in seen.items():
        print(f"    субтитры «{title}»: {value!r}")
    print(f"    снимок: {report.get('snapshot', '—')}, подсветка: {report.get('ambientFrame')}, шаг кадра: {report.get('stepDelta', 0):.3f} с")
    for p in problems:
        print(f"    ! {p}")
    if problems and required:
        failures.append(name)

if failures:
    print("\nПровалено:", ", ".join(failures))
    sys.exit(1)
