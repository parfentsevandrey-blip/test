from mapgen import context_map
# key, fac_lat, fac_lon, center_lat, center_lon, zoom, label
ZONES = [
    ("asml",      51.4047, 5.4174, 51.423, 5.443, 12, "ASML Global HQ"),
    ("htc",       51.4089, 5.4604, 51.426, 5.462, 12, "High Tech Campus"),
    ("nijmegen",  51.8248, 5.8173, 51.833, 5.835, 12, "NXP Nijmegen"),
    ("amsterdam", 52.3564, 4.9508, 52.362, 4.928, 12, "Amsterdam Science Park"),
    ("eemshaven", 53.4244, 6.8592, 53.325, 6.715, 11, "Google Eemshaven"),
    ("agriport",  52.7656, 5.0380, 52.705, 5.020, 11, "Agriport A7 · Microsoft"),
    ("delft",     52.0008, 4.3754, 52.000, 4.385, 11, "TU Delft · QuTech"),
]
for key, flat, flon, clat, clon, z, label in ZONES:
    out = f"assets/map_{key}.jpg"
    try:
        context_map(flat, flon, clat, clon, z, label, out)
        import os
        print("OK", out, os.path.getsize(out) // 1024, "KB")
    except Exception as e:
        print("FAIL", key, e)
print("DONE")
