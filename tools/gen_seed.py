#!/usr/bin/env python3
"""Generates backend/app/src/main/resources/db/seed/V100__seed_catalog.sql (deterministic).
Run: python3 tools/gen_seed.py > backend/app/src/main/resources/db/seed/V100__seed_catalog.sql"""
import json, random

random.seed(42)
q = lambda s: "'" + str(s).replace("'", "''") + "'"
import re
slug = lambda s: re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")

CATEGORIES = [  # (name, slug, parent_slug, description, sort)
    ("Electronics", "electronics", None, "Computers, phones and audio", 1),
    ("Laptops", "laptops", "electronics", "Everyday, business and gaming laptops", 1),
    ("Smartphones", "smartphones", "electronics", "Android phones and iPhones", 2),
    ("Headphones", "headphones", "electronics", "Wireless earbuds and over-ear headphones", 3),
    ("Home & Living", "home-living", None, "Smart devices and kitchen appliances", 2),
    ("Smart Home", "smart-home", "home-living", "Speakers, cameras, bulbs and plugs", 1),
    ("Kitchen", "kitchen", "home-living", "Appliances for everyday cooking", 2),
    ("Fitness", "fitness", None, "Home gym and activity gear", 3),
]

# category slug: (sku prefix, start, [ (brand, model, price, rating, rating_count, specs, tags) ])
L = lambda ram, cpu, storage, disp, wt, bat, gpu="Integrated": {"ram_gb": ram, "cpu": cpu, "storage": storage, "display": disp, "weight_kg": wt, "battery_hours": bat, "gpu": gpu}
LAPTOPS = [
    ("Dell", "XPS 13 Plus", 1299, 4.6, 812, L(16, "Intel Core i7", "512GB SSD", "13.4in OLED", 1.24, 12), "ultralight premium work travel"),
    ("Dell", "Inspiron 15", 649, 4.2, 1530, L(8, "Intel Core i5", "512GB SSD", "15.6in FHD", 1.8, 8), "budget student everyday"),
    ("Dell", "G15 Gaming", 1099, 4.4, 640, L(16, "AMD Ryzen 7", "1TB SSD", "15.6in 165Hz", 2.5, 6, "NVIDIA RTX 4050"), "gaming performance"),
    ("Lenovo", "ThinkPad E14", 779, 4.3, 920, L(16, "Intel Core i5", "512GB SSD", "14in FHD", 1.64, 10), "business reliable keyboard"),
    ("Lenovo", "IdeaPad Slim 5", 699, 4.4, 1105, L(16, "AMD Ryzen 5", "512GB SSD", "14in OLED", 1.46, 11), "lightweight student oled"),
    ("Lenovo", "Legion 5", 1249, 4.6, 702, L(16, "AMD Ryzen 7", "1TB SSD", "16in 165Hz", 2.4, 7, "NVIDIA RTX 4060"), "gaming performance"),
    ("Lenovo", "Yoga 7 2-in-1", 999, 4.3, 388, L(16, "Intel Core i7", "512GB SSD", "14in touch", 1.5, 10), "convertible touchscreen creative"),
    ("HP", "Pavilion 15", 599, 4.1, 1420, L(8, "Intel Core i5", "256GB SSD", "15.6in FHD", 1.74, 7), "budget everyday"),
    ("HP", "Envy x360", 949, 4.4, 540, L(16, "AMD Ryzen 7", "1TB SSD", "13.3in OLED touch", 1.3, 13), "convertible oled premium"),
    ("HP", "Victus 16", 899, 4.2, 610, L(16, "Intel Core i5", "512GB SSD", "16.1in 144Hz", 2.3, 6, "NVIDIA RTX 3050"), "gaming budget"),
    ("Apple", "MacBook Air M2", 1099, 4.8, 3020, L(8, "Apple M2", "256GB SSD", "13.6in Retina", 1.24, 18), "ultralight premium battery"),
    ("Apple", "MacBook Air M3 16GB", 1399, 4.8, 1210, L(16, "Apple M3", "512GB SSD", "13.6in Retina", 1.24, 18), "ultralight premium creative"),
    ("Apple", "MacBook Pro 14 M3 Pro", 1999, 4.9, 760, L(18, "Apple M3 Pro", "512GB SSD", "14.2in XDR", 1.6, 17), "professional video creative"),
    ("Asus", "Zenbook 14 OLED", 849, 4.5, 880, L(16, "Intel Core Ultra 7", "512GB SSD", "14in OLED", 1.2, 14), "ultralight oled travel"),
    ("Asus", "ROG Strix G16", 1599, 4.7, 433, L(32, "Intel Core i9", "1TB SSD", "16in 240Hz", 2.5, 6, "NVIDIA RTX 4070"), "gaming highend"),
    ("Asus", "TUF Gaming F15", 849, 4.4, 1260, L(16, "Intel Core i7", "512GB SSD", "15.6in 144Hz", 2.2, 7, "NVIDIA RTX 4050"), "gaming durable"),
    ("Acer", "Aspire 5", 549, 4.1, 1980, L(8, "Intel Core i3", "256GB SSD", "15.6in FHD", 1.8, 8), "budget student"),
    ("Acer", "Swift Go 14", 799, 4.3, 570, L(16, "Intel Core Ultra 5", "512GB SSD", "14in OLED", 1.32, 12), "lightweight oled"),
]
P = lambda ram, storage, disp, cam, bat, chip, g5=True: {"ram_gb": ram, "storage": storage, "display": disp, "camera_mp": cam, "battery_mah": bat, "chip": chip, "5g": g5}
PHONES = [
    ("Apple", "iPhone 15", 799, 4.7, 4100, P(6, "128GB", "6.1in OLED", 48, 3349, "A16 Bionic"), "camera premium ios"),
    ("Apple", "iPhone 15 Pro", 999, 4.8, 2700, P(8, "256GB", "6.1in ProMotion", 48, 3274, "A17 Pro"), "camera premium titanium"),
    ("Apple", "iPhone SE", 429, 4.4, 1900, P(4, "64GB", "4.7in LCD", 12, 2018, "A15 Bionic"), "compact budget ios"),
    ("Samsung", "Galaxy S24", 799, 4.6, 2300, P(8, "256GB", "6.2in AMOLED", 50, 4000, "Snapdragon 8 Gen 3"), "camera flagship android"),
    ("Samsung", "Galaxy S24 Ultra", 1299, 4.8, 1500, P(12, "512GB", "6.8in AMOLED", 200, 5000, "Snapdragon 8 Gen 3"), "camera flagship stylus"),
    ("Samsung", "Galaxy A55", 449, 4.3, 3100, P(8, "128GB", "6.6in AMOLED", 50, 5000, "Exynos 1480"), "midrange battery"),
    ("Samsung", "Galaxy A15", 199, 4.0, 4200, P(4, "128GB", "6.5in AMOLED", 50, 5000, "Helio G99", False), "budget battery"),
    ("Google", "Pixel 8", 699, 4.5, 1700, P(8, "128GB", "6.2in OLED", 50, 4575, "Tensor G3"), "camera clean android ai"),
    ("Google", "Pixel 8a", 499, 4.4, 1300, P(8, "128GB", "6.1in OLED", 64, 4492, "Tensor G3"), "camera midrange ai"),
    ("OnePlus", "12", 799, 4.5, 980, P(12, "256GB", "6.82in LTPO", 50, 5400, "Snapdragon 8 Gen 3"), "fast charging flagship"),
    ("OnePlus", "Nord CE4", 299, 4.2, 2100, P(8, "128GB", "6.7in AMOLED", 50, 5500, "Snapdragon 7 Gen 3"), "budget fast charging"),
    ("Xiaomi", "Redmi Note 13 Pro", 329, 4.3, 5200, P(8, "256GB", "6.67in AMOLED", 200, 5100, "Snapdragon 7s Gen 2"), "camera value"),
    ("Xiaomi", "Poco X6", 279, 4.2, 3300, P(8, "256GB", "6.67in AMOLED", 64, 5100, "Snapdragon 7s Gen 2"), "gaming value"),
    ("Xiaomi", "14T", 649, 4.4, 640, P(12, "256GB", "6.67in AMOLED", 50, 5000, "Dimensity 8300 Ultra"), "camera leica"),
    ("Motorola", "Edge 50 Fusion", 349, 4.2, 870, P(8, "256GB", "6.7in pOLED", 50, 5000, "Snapdragon 7s Gen 2"), "clean android midrange"),
    ("Motorola", "Moto G54", 189, 4.0, 2600, P(8, "128GB", "6.5in LCD", 50, 6000, "Dimensity 7020"), "budget battery"),
    ("Nothing", "Phone (2a)", 349, 4.3, 910, P(8, "128GB", "6.7in AMOLED", 50, 5000, "Dimensity 7200 Pro"), "design glyph midrange"),
    ("Samsung", "Galaxy Z Flip5", 999, 4.4, 430, P(8, "256GB", "6.7in foldable", 12, 3700, "Snapdragon 8 Gen 2"), "foldable compact"),
]
H = lambda kind, anc, bat, wl=True, drv="40mm": {"type": kind, "noise_cancelling": anc, "battery_hours": bat, "wireless": wl, "driver": drv}
HEADPHONES = [
    ("Sony", "WH-1000XM5", 349, 4.8, 5400, H("Over-ear", True, 30, True, "30mm"), "noise cancelling travel premium"),
    ("Sony", "WF-1000XM5", 299, 4.6, 2200, H("In-ear", True, 8, True, "8.4mm"), "noise cancelling earbuds compact"),
    ("Sony", "WH-CH720N", 149, 4.4, 1800, H("Over-ear", True, 35, True, "30mm"), "noise cancelling budget"),
    ("Bose", "QuietComfort Ultra", 429, 4.7, 1900, H("Over-ear", True, 24, True, "35mm"), "noise cancelling premium comfort"),
    ("Bose", "QuietComfort Earbuds II", 279, 4.5, 1300, H("In-ear", True, 6, True, "9.3mm"), "noise cancelling earbuds"),
    ("Apple", "AirPods Pro 2", 249, 4.8, 6100, H("In-ear", True, 6, True, "11mm"), "noise cancelling earbuds ios"),
    ("Apple", "AirPods Max", 549, 4.5, 1100, H("Over-ear", True, 20, True, "40mm"), "noise cancelling premium ios"),
    ("Samsung", "Galaxy Buds2 Pro", 199, 4.4, 2100, H("In-ear", True, 5, True, "10mm"), "noise cancelling earbuds android"),
    ("JBL", "Tune 520BT", 59, 4.3, 7200, H("On-ear", False, 57, True, "32mm"), "budget battery wireless"),
    ("JBL", "Live 770NC", 199, 4.4, 980, H("Over-ear", True, 50, True, "40mm"), "noise cancelling battery"),
    ("JBL", "Endurance Peak 3", 99, 4.3, 1500, H("In-ear", False, 10, True, "10mm"), "sport earbuds waterproof"),
    ("Sennheiser", "Momentum 4", 349, 4.6, 840, H("Over-ear", True, 60, True, "42mm"), "audiophile battery premium"),
    ("Sennheiser", "HD 560S", 169, 4.6, 1600, H("Over-ear", False, 0, False, "38mm"), "wired audiophile open-back"),
    ("Audio-Technica", "ATH-M50x", 149, 4.7, 4800, H("Over-ear", False, 0, False, "45mm"), "wired studio monitor"),
    ("Anker", "Soundcore Q30", 79, 4.4, 9100, H("Over-ear", True, 40, True, "40mm"), "noise cancelling budget"),
    ("Anker", "Soundcore P40i", 59, 4.2, 3400, H("In-ear", True, 12, True, "10mm"), "noise cancelling earbuds budget"),
    ("OnePlus", "Buds Pro 2", 149, 4.3, 1200, H("In-ear", True, 9, True, "11mm"), "noise cancelling earbuds"),
    ("Beats", "Studio Pro", 349, 4.3, 1400, H("Over-ear", True, 40, True, "40mm"), "noise cancelling bass"),
]
SMART = [
    ("Amazon", "Echo Dot 5th Gen", 49, 4.6, 9800, {"type": "Smart speaker", "assistant": "Alexa", "wifi": True, "voice": True}, "speaker alexa compact"),
    ("Amazon", "Echo Show 8", 129, 4.5, 4100, {"type": "Smart display", "assistant": "Alexa", "screen": "8in", "camera": True}, "display speaker alexa"),
    ("Google", "Nest Mini", 49, 4.4, 5300, {"type": "Smart speaker", "assistant": "Google Assistant", "wifi": True, "voice": True}, "speaker google compact"),
    ("Google", "Nest Hub 2nd Gen", 99, 4.4, 2600, {"type": "Smart display", "assistant": "Google Assistant", "screen": "7in", "sleep_sensing": True}, "display speaker google"),
    ("Google", "Nest Cam Indoor", 99, 4.2, 1900, {"type": "Security camera", "resolution": "1080p", "night_vision": True, "wired": True}, "security camera indoor"),
    ("Ring", "Video Doorbell", 99, 4.4, 8100, {"type": "Doorbell", "resolution": "1080p", "night_vision": True, "wired": False}, "security doorbell camera"),
    ("Ring", "Indoor Cam 2nd Gen", 59, 4.4, 5000, {"type": "Security camera", "resolution": "1080p", "night_vision": True, "wired": True}, "security camera indoor budget"),
    ("Philips", "Hue White Ambiance 2-pack", 49, 4.5, 3700, {"type": "Smart bulb", "lumens": 800, "colour": False, "protocol": "Zigbee"}, "bulb lighting"),
    ("Philips", "Hue Color Starter Kit", 189, 4.6, 2900, {"type": "Smart bulb", "lumens": 1100, "colour": True, "protocol": "Zigbee"}, "bulb lighting colour starter"),
    ("TP-Link", "Kasa Smart Plug 4-pack", 29, 4.5, 11400, {"type": "Smart plug", "wifi": True, "energy_monitor": False, "voice": True}, "plug budget energy"),
    ("TP-Link", "Tapo C210 Camera", 35, 4.4, 6200, {"type": "Security camera", "resolution": "2K", "night_vision": True, "wired": True}, "security camera budget"),
    ("Eufy", "Video Doorbell 2K", 149, 4.4, 2200, {"type": "Doorbell", "resolution": "2K", "night_vision": True, "wired": False}, "security doorbell no-subscription"),
    ("Eufy", "RoboVac 11S", 169, 4.3, 7300, {"type": "Robot vacuum", "suction_pa": 1300, "runtime_min": 100, "wifi": False}, "vacuum robot"),
    ("Roborock", "Q Revo", 499, 4.6, 1100, {"type": "Robot vacuum", "suction_pa": 5500, "runtime_min": 180, "wifi": True}, "vacuum robot mop premium"),
    ("Nest", "Learning Thermostat", 249, 4.5, 3200, {"type": "Thermostat", "wifi": True, "learning": True, "energy_saving": True}, "thermostat energy"),
    ("Sonos", "Era 100", 249, 4.6, 1500, {"type": "Smart speaker", "assistant": "Alexa", "wifi": True, "stereo": True}, "speaker audio premium"),
    ("Apple", "HomePod mini", 99, 4.5, 4600, {"type": "Smart speaker", "assistant": "Siri", "wifi": True, "voice": True}, "speaker ios compact"),
    ("Arlo", "Pro 4 Spotlight 2-cam", 349, 4.3, 2400, {"type": "Security camera", "resolution": "2K", "night_vision": True, "wired": False}, "security camera outdoor wireless"),
]
K = lambda t, w, cap, ex="": {"type": t, "power_w": w, "capacity": cap, **({"extra": ex} if ex else {})}
KITCHEN = [
    ("Philips", "Air Fryer XL", 129, 4.6, 6800, K("Air fryer", 1900, "6.2L"), "air fryer healthy"),
    ("Instant Pot", "Duo 7-in-1", 99, 4.7, 21000, K("Pressure cooker", 1000, "5.7L"), "pressure cooker multicooker"),
    ("Ninja", "Foodi Dual Air Fryer", 169, 4.7, 9400, K("Air fryer", 2470, "7.6L", "dual basket"), "air fryer dual large"),
    ("Ninja", "Professional Blender", 99, 4.6, 12000, K("Blender", 1000, "1.9L"), "blender smoothie"),
    ("NutriBullet", "Pro 900", 79, 4.5, 15000, K("Blender", 900, "0.9L"), "blender smoothie compact"),
    ("Vitamix", "E310", 349, 4.8, 3100, K("Blender", 1380, "1.4L"), "blender premium powerful"),
    ("Breville", "Barista Express", 599, 4.6, 2900, K("Espresso machine", 1600, "2L", "built-in grinder"), "coffee espresso grinder"),
    ("De'Longhi", "Magnifica Start", 449, 4.4, 2100, K("Espresso machine", 1450, "1.8L", "bean to cup"), "coffee espresso automatic"),
    ("Keurig", "K-Mini", 89, 4.4, 8800, K("Coffee maker", 1200, "0.35L"), "coffee pod compact"),
    ("Nespresso", "Vertuo Pop", 129, 4.5, 4300, K("Coffee maker", 1260, "0.6L"), "coffee capsule compact"),
    ("KitchenAid", "Artisan Stand Mixer", 449, 4.8, 7600, K("Stand mixer", 300, "4.8L"), "mixer baking premium"),
    ("Cuisinart", "4-Slice Toaster", 59, 4.3, 5200, K("Toaster", 1800, "4 slices"), "toaster breakfast"),
    ("OXO", "Good Grips Food Scale", 49, 4.6, 6100, K("Scale", 0, "5kg"), "scale baking"),
    ("Tefal", "Easy Fry Oven", 149, 4.3, 1800, K("Air fryer oven", 2200, "11L"), "air fryer oven large"),
    ("Morphy Richards", "Electric Kettle 1.7L", 29, 4.4, 9700, K("Kettle", 3000, "1.7L"), "kettle fast budget"),
    ("Bosch", "MUM5 Food Processor", 199, 4.5, 2600, K("Food processor", 1000, "3.9L"), "food processor kneading"),
    ("OXO", "Good Grips Rice Cooker", 119, 4.5, 3300, K("Rice cooker", 700, "1.8L"), "rice cooker"),
    ("Smeg", "Retro Toaster 2-slice", 189, 4.4, 1900, K("Toaster", 950, "2 slices"), "toaster retro design"),
]
F = lambda t, extra: {"type": t, **extra}
FITNESS = [
    ("Bowflex", "SelectTech 552 Dumbbells", 429, 4.7, 5400, F("Adjustable dumbbells", {"weight_kg": "2-24", "pair": True}), "dumbbells strength home gym"),
    ("Decathlon", "Domyos Yoga Mat 8mm", 25, 4.5, 8800, F("Yoga mat", {"thickness_mm": 8, "non_slip": True}), "yoga mat budget"),
    ("Manduka", "PRO Yoga Mat", 129, 4.8, 3900, F("Yoga mat", {"thickness_mm": 6, "non_slip": True, "lifetime_warranty": True}), "yoga mat premium"),
    ("NordicTrack", "Commercial 1750 Treadmill", 1799, 4.6, 1600, F("Treadmill", {"max_speed_kmh": 19, "incline": "15%", "screen": "14in"}), "treadmill running cardio"),
    ("Sunny Health", "Folding Treadmill", 349, 4.2, 3100, F("Treadmill", {"max_speed_kmh": 14, "foldable": True}), "treadmill budget compact"),
    ("Peloton", "Bike", 1445, 4.5, 2100, F("Exercise bike", {"screen": "22in", "resistance": "magnetic"}), "bike cycling cardio"),
    ("Schwinn", "IC4 Bike", 799, 4.6, 5600, F("Exercise bike", {"resistance": "magnetic", "bluetooth": True}), "bike cycling cardio"),
    ("Garmin", "Forerunner 265", 449, 4.7, 2800, F("GPS running watch", {"battery_days": 13, "gps": True, "amoled": True}), "watch running tracker gps"),
    ("Garmin", "Venu Sq 2", 249, 4.4, 2300, F("Fitness watch", {"battery_days": 11, "gps": True, "amoled": True}), "watch tracker"),
    ("Fitbit", "Charge 6", 159, 4.3, 7400, F("Fitness tracker", {"battery_days": 7, "gps": True, "heart_rate": True}), "tracker heart rate"),
    ("Xiaomi", "Smart Band 8", 39, 4.3, 12600, F("Fitness tracker", {"battery_days": 16, "gps": False, "heart_rate": True}), "tracker budget battery"),
    ("Apple", "Watch SE", 249, 4.6, 5100, F("Smartwatch", {"battery_days": 1.5, "gps": True, "ios_only": True}), "watch ios tracker"),
    ("TRX", "Home2 Suspension Trainer", 199, 4.6, 2700, F("Suspension trainer", {"anchors": "door/ceiling"}), "bodyweight strength travel"),
    ("Rogue", "Echo Bumper Plates 100kg Set", 349, 4.7, 800, F("Weight plates", {"total_kg": 100, "bumper": True}), "barbell strength"),
    ("Theragun", "Mini Massage Gun", 199, 4.6, 3300, F("Massage gun", {"battery_hours": 2.5, "speeds": 3}), "recovery massage"),
    ("Optp", "Resistance Band Set", 22, 4.6, 14000, F("Resistance bands", {"levels": 5, "latex_free": False}), "bands strength budget"),
    ("Hyperice", "Normatec Go", 299, 4.3, 700, F("Compression boots", {"portable": True, "battery_hours": 5}), "recovery compression"),
    ("Concept2", "RowErg", 990, 4.9, 1900, F("Rowing machine", {"resistance": "air", "monitor": "PM5"}), "rowing cardio full-body"),
]

DATA = {
    "laptops": ("LAP", 101, LAPTOPS), "smartphones": ("PHN", 201, PHONES), "headphones": ("HDP", 301, HEADPHONES),
    "smart-home": ("SMH", 401, SMART), "kitchen": ("KIT", 501, KITCHEN), "fitness": ("FIT", 601, FITNESS),
}
LOW_STOCK = {"LAP105", "PHN203", "HDP307", "SMH405", "KIT509", "FIT611", "LAP113"}   # fast sellers, little stock
OUT_OF_STOCK = {"FIT618", "KIT518"}
POPULAR = LOW_STOCK | {"PHN201", "PHN204", "HDP306", "LAP111", "SMH401", "KIT502", "KIT504"}
WAREHOUSES = [("WH-DEL", "Delhi Fulfilment Centre", "New Delhi", "IN", "IN"),
              ("WH-BLR", "Bengaluru Fulfilment Centre", "Bengaluru", "IN", "IN"),
              ("WH-NWK", "Newark Fulfilment Centre", "Newark", "US", "GLOBAL")]

out = []
w = out.append
w("-- Generated by tools/gen_seed.py (deterministic). Demo catalog: 8 categories, 18 brands+, 108 products, stock, sales history.")
w("-- Owner: Lovish (catalog + inventory). Loaded by Flyway from classpath:db/seed.\n")
for name, s, parent, desc, sort in CATEGORIES:
    parent_sql = f"(SELECT id FROM categories WHERE slug = {q(parent)})" if parent else "NULL"
    w(f"INSERT INTO categories (parent_id, name, slug, description, sort_order) VALUES ({parent_sql}, {q(name)}, {q(s)}, {q(desc)}, {sort});")
w("")
brands = sorted({b for _, (_, _, items) in DATA.items() for (b, *_rest) in items})
for b in brands:
    w(f"INSERT INTO brands (name, slug) VALUES ({q(b)}, {q(slug(b))});")
w("")
SINGULAR = {"laptops": "laptop", "smartphones": "smartphone", "headphones": "headphones", "smart-home": "smart home device",
            "kitchen": "kitchen appliance", "fitness": "fitness product"}
inv_rows, mv_rows, img_rows = [], [], []
for cslug, (prefix, start, items) in DATA.items():
    for i, (brand, model, price, rating, rc, specs, tags) in enumerate(items):
        sku = f"{prefix}{start + i}"
        name = f"{brand} {model}"
        spec_bits = ", ".join(f"{k.replace('_', ' ')} {v}" for k, v in list(specs.items())[:4])
        desc = f"{name} {SINGULAR[cslug]}. {spec_bits}. Good for: {tags}."
        w(f"INSERT INTO products (sku, name, brand_id, category_id, description, price, rating, rating_count, specs) VALUES "
          f"({q(sku)}, {q(name)}, (SELECT id FROM brands WHERE slug = {q(slug(brand))}), (SELECT id FROM categories WHERE slug = {q(cslug)}), "
          f"{q(desc)}, {price}, {rating}, {rc}, {q(json.dumps(specs))}::jsonb);")
        for n in (1, 2):
            img_rows.append((sku, f"https://picsum.photos/seed/{sku}-{n}/800/600", f"{name} image {n}", n, n == 1))
        for wh in WAREHOUSES:
            if sku in OUT_OF_STOCK: qty = 0
            elif sku in LOW_STOCK: qty = random.randint(1, 3)
            else: qty = random.randint(25, 220)
            inv_rows.append((sku, wh[0], qty, 10))
            # last-30-days sales
            units = random.randint(25, 45) if sku in POPULAR else random.randint(0, 14)
            if sku in OUT_OF_STOCK: units = random.randint(10, 20)
            left = units
            while left > 0:
                n = min(left, random.randint(1, 4))
                mv_rows.append((sku, wh[0], -n, "SALE", random.randint(0, 29)))
                left -= n
w("")
for code, name, city, country, region in WAREHOUSES:
    w(f"INSERT INTO warehouses (code, name, city, country, region) VALUES ({q(code)}, {q(name)}, {q(city)}, {q(country)}, {q(region)});")
w("")
w("INSERT INTO product_images (product_id, url, alt_text, sort_order, is_primary)\nSELECT p.id, v.url, v.alt, v.sort, v.prim FROM (VALUES")
w(",\n".join(f"  ({q(s)}, {q(u)}, {q(a)}, {n}, {str(pr).upper()})" for s, u, a, n, pr in img_rows))
w(") AS v(sku, url, alt, sort, prim) JOIN products p ON p.sku = v.sku;\n")
w("INSERT INTO inventory (product_id, warehouse_id, quantity_on_hand, reorder_level)\nSELECT p.id, wh.id, v.qty, v.reorder FROM (VALUES")
w(",\n".join(f"  ({q(s)}, {q(c)}, {qt}, {r})" for s, c, qt, r in inv_rows))
w(") AS v(sku, wh, qty, reorder) JOIN products p ON p.sku = v.sku JOIN warehouses wh ON wh.code = v.wh;\n")
w("INSERT INTO inventory_movements (product_id, warehouse_id, change_qty, reason, created_at)\nSELECT p.id, wh.id, v.chg, v.reason, now() - make_interval(days => v.days) FROM (VALUES")
w(",\n".join(f"  ({q(s)}, {q(c)}, {ch}, {q(rs)}, {d})" for s, c, ch, rs, d in mv_rows))
w(") AS v(sku, wh, chg, reason, days) JOIN products p ON p.sku = v.sku JOIN warehouses wh ON wh.code = v.wh;")
w("-- opening stock ledger: current stock plus what was sold in the last 30 days, so the ledger adds up")
w("INSERT INTO inventory_movements (product_id, warehouse_id, change_qty, reason, created_at)\n"
  "SELECT i.product_id, i.warehouse_id, i.quantity_on_hand + s.sold, 'RESTOCK', now() - interval '45 days'\n"
  "FROM inventory i CROSS JOIN LATERAL (SELECT COALESCE(-SUM(m.change_qty), 0)::INT AS sold FROM inventory_movements m\n"
  "  WHERE m.product_id = i.product_id AND m.warehouse_id = i.warehouse_id AND m.reason = 'SALE') s\n"
  "WHERE i.quantity_on_hand + s.sold > 0;")
print("\n".join(out))
