#!/usr/bin/env bash
# Набор Matroska/WebM-файлов для самопроверки демультиплексора (только для CI).
set -euo pipefail
OUT="$1"
mkdir -p "$OUT"
cd "$OUT"
FF="ffmpeg -hide_banner -loglevel error -y"

cat > subs.srt <<'SRT'
1
00:00:01,000 --> 00:00:03,000
Привет, это <i>Lumen</i>

2
00:00:04,000 --> 00:00:06,500
Second line
SRT

cat > chapters.txt <<'META'
;FFMETADATA1
title=Lumen fixture
[CHAPTER]
TIMEBASE=1/1000
START=0
END=5000
title=Начало
[CHAPTER]
TIMEBASE=1/1000
START=5000
END=12000
title=Финал
META

SRC="-f lavfi -i testsrc2=size=1280x720:rate=30 -f lavfi -i sine=frequency=440:sample_rate=48000 -f lavfi -i sine=frequency=660:sample_rate=48000"

# H.264 с B-кадрами, два звука (AAC стерео + AC-3 5.1), SRT, главы
$FF $SRC -i subs.srt -i chapters.txt -t 12 \
  -map 0:v -map 1:a -map 2:a -map 3:s -map_metadata 4 -map_chapters 4 \
  -c:v libx264 -preset veryfast -bf 3 -pix_fmt yuv420p \
  -c:a:0 aac -ac:a:0 2 -c:a:1 ac3 -ac:a:1 6 -c:s srt \
  -metadata:s:a:0 language=rus -metadata:s:a:1 language=eng -metadata:s:s:0 language=rus \
  h264_aac_ac3_srt.mkv

# HEVC 10 бит с метками HDR10 и E-AC-3
$FF $SRC -t 8 -map 0:v -map 1:a \
  -c:v libx265 -preset ultrafast -pix_fmt yuv420p10le -tag:v hvc1 \
  -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc \
  -x265-params log-level=error \
  -c:a eac3 -b:a 384k hevc10_hdr_eac3.mkv

# Поток без размеров элементов (как запись трансляции): кластеры неизвестной длины
$FF $SRC -t 6 -map 0:v -map 1:a -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac -f matroska pipe:1 > live_unknown_size.mkv

# MP3 и ASS-субтитры
cat > subs.ass <<'ASS'
[Script Info]
ScriptType: v4.00+

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,{\b1}Жирный{\b0} текст\Nвторая строка
ASS
$FF $SRC -i subs.ass -t 6 -map 0:v -map 1:a -map 3:s -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a libmp3lame -c:s ass h264_mp3_ass.mkv

# WebM: VP9 + Opus (VP9 перекодируется в HEVC, Opus пропускается с пометкой)
$FF $SRC -t 5 -map 0:v -map 1:a -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -b:v 1M -c:a libopus vp9_opus.webm

# AV1 (аппаратный декодер есть только на M3 и новее — на раннере может не декодироваться)
$FF $SRC -t 4 -map 0:v -map 1:a -c:v libsvtav1 -preset 12 -pix_fmt yuv420p -c:a aac av1_aac.mkv || true

ls -la
