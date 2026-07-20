from mapgen import satellite
import os
os.makedirs("assets", exist_ok=True)

# (key, lat, lon, zoom, title, subtitle)
ZONES = [
    ("asml_veldhoven", 51.41056, 5.42622, 15,
     "ASML — Велдховен, промзона De Run", "Brainport Eindhoven · Северный Брабант · спутниковый снимок"),
    ("htc_eindhoven", 51.41045, 5.45560, 16,
     "High Tech Campus Eindhoven", "«Самый умный квадратный километр Европы» · Эйндховен"),
    ("amsterdam_sciencepark", 52.35620, 4.95300, 15,
     "Amsterdam Science Park и узел AMS-IX", "Амстердам · дата-центры и интернет-обмен"),
    ("google_eemshaven", 53.43880, 6.83540, 14,
     "Дата-центр Google — Эмсхавен", "Провинция Гронинген · порт и ветропарки"),
    ("agriport_middenmeer", 52.80800, 5.00100, 14,
     "Agriport A7 — Мидденмер (Microsoft)", "Холландс Крон · Северная Голландия"),
    ("nxp_nijmegen", 51.82870, 5.87560, 15,
     "NXP Semiconductors — Неймеген", "Завод по производству чипов · Гелдерланд"),
    ("qutech_delft", 52.00160, 4.37300, 15,
     "TU Delft / QuTech — Делфт", "Квантовые технологии и нанонаука · Южная Голландия"),
]

for key, lat, lon, z, title, sub in ZONES:
    out = f"assets/sat_{key}.jpg"
    try:
        satellite(lat, lon, out, z=z, title=title, subtitle=sub)
        print("OK", out, os.path.getsize(out))
    except Exception as e:
        print("FAIL", key, e)
print("ALL DONE")
