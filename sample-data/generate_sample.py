"""
Generates a SYNTHETIC smoke-test CSV (sample-data/sample_electronics_reviews.csv).

It is NOT real Amazon data - it only mimics the column layout of the public
"Consumer Reviews of Amazon Products" (Datafiniti, Kaggle) dataset so you can test the
upload -> analysis -> dashboard flow in seconds. For your evaluation, upload a real
public dataset (see README.md).
"""
import csv
import random
from datetime import date, timedelta
from pathlib import Path

random.seed(42)

PRODUCTS = {
    # name, asin, per-aspect probability that an opinion is positive
    ("Nimbus X5 Smartphone", "B0SAMPLE01"): {"Camera": .85, "Battery": .30, "Display": .80, "Price": .35, "Performance": .70, "Build Quality": .65, "Sound": .55, "Heating": .40},
    ("Vertex Pro 12 Smartphone", "B0SAMPLE02"): {"Camera": .55, "Battery": .85, "Display": .60, "Price": .75, "Performance": .60, "Build Quality": .50, "Sound": .65, "Heating": .70},
    ("Aurora Buds Wireless Earbuds", "B0SAMPLE03"): {"Battery": .60, "Price": .70, "Build Quality": .45, "Sound": .80, "Connectivity": .40},
}

PHRASES = {
    "Camera": (["The camera quality is excellent", "Photos come out sharp and vibrant", "The night mode on the camera is amazing", "Selfies look great"],
               ["The camera is disappointing in low light", "Photos are grainy and blurry", "The camera struggles indoors"]),
    "Battery": (["The battery lasts all day", "Battery life is fantastic", "It charges fast and the battery holds up well", "Battery easily goes 2 days"],
                ["The battery drains quickly", "Battery life is terrible", "The battery does not last even a full day", "Charging is slow"]),
    "Display": (["The display is beautiful", "The screen is bright and crisp", "Colors on the screen look stunning"],
                ["The screen is too dim outdoors", "The display looks washed out", "The screen scratches easily"]),
    "Price": (["Great value for money", "It is reasonably priced", "Totally worth the price"],
              ["It is overpriced", "Way too expensive for what you get", "Not worth the money"]),
    "Performance": (["Performance is super fast with no lag", "Apps open instantly and multitasking is smooth"],
                    ["It lags when switching apps", "Performance is sluggish and it hangs often"]),
    "Build Quality": (["The build quality feels premium", "It is sturdy and well built"],
                      ["It feels cheap and plastic", "The build is flimsy"]),
    "Sound": (["The sound is rich with deep bass", "Speakers are loud and clear"],
              ["Audio is muffled", "The speaker sounds tinny"]),
    "Heating": (["It stays cool even while gaming"], ["The phone heats up while gaming", "It overheats when charging"]),
    "Connectivity": (["Bluetooth pairing is seamless"], ["Bluetooth keeps disconnecting", "The connection drops constantly"]),
}
CONNECTORS = [", but ", ". ", ", although ", ". However, ", " and "]
OPENERS = ["", "", "Bought this last month. ", "Overall mixed feelings. ", "Upgraded from my old device. "]
TITLES_POS = ["Love it", "Great purchase", "Highly recommend", "Excellent device"]
TITLES_NEG = ["Disappointed", "Not as expected", "Could be better", "Returning it"]
TITLES_MIX = ["Mixed feelings", "Good but not perfect", "Decent overall"]


def review_for(probs):
    aspects = random.sample(list(probs), k=random.randint(2, min(4, len(probs))))
    parts, pos = [], 0
    for a in aspects:
        good = random.random() < probs[a]
        pos += good
        parts.append(random.choice(PHRASES[a][0 if good else 1]))
    text = parts[0]
    for p in parts[1:]:
        c = random.choice(CONNECTORS)
        text += c + (p if c == ". " else p[0].lower() + p[1:])
    text = random.choice(OPENERS) + text + "."
    ratio = pos / len(aspects)
    rating = max(1, min(5, round(1 + ratio * 4 + random.uniform(-0.6, 0.6))))
    title = random.choice(TITLES_POS if ratio > .7 else TITLES_NEG if ratio < .3 else TITLES_MIX)
    return text, rating, title


def main():
    out = Path(__file__).with_name("sample_electronics_reviews.csv")
    start = date(2025, 11, 1)
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["id", "name", "asins", "brand", "categories", "reviews.date", "reviews.rating",
                    "reviews.text", "reviews.title", "reviews.username"])
        rid = 1
        for (name, asin), probs in PRODUCTS.items():
            for _ in range(150):
                text, rating, title = review_for(probs)
                d = start + timedelta(days=random.randint(0, 364))
                w.writerow([rid, name, asin, name.split()[0], "Electronics", d.isoformat(), rating, text, title, f"user{random.randint(1000, 9999)}"])
                rid += 1
    print(f"wrote {rid - 1} reviews -> {out}")


if __name__ == "__main__":
    main()
