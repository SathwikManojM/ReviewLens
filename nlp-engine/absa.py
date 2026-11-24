"""
ReviewLens - Aspect-Based Sentiment Analysis (ABSA) engine.

Pipeline for every review:
  1. Pre-processing   : strip HTML/URLs/emojis, normalise whitespace & elongations,
                        expand contractions (keeps negations such as "not" intact).
  2. Segmentation     : split into sentences, then into clauses on contrast markers
                        ("but", "although", "however", ...) and clause commas, so that
                        "camera is great but battery drains" yields two opinion units.
  3. Aspect extraction: match each clause against an aspect lexicon (canonical aspect ->
                        synonyms / multi-word terms). Pronoun clauses ("it lasts all day")
                        inherit the aspect of the previous clause.
  4. Aspect sentiment : VADER compound score of the clause, augmented with an
                        electronics domain lexicon and aspect-specific opinion phrases
                        (e.g. battery + "drains quickly" -> negative, price + "worth it"
                        -> positive). Negation is handled by VADER and by the phrase rules.
  5. Aggregation      : one sentiment per (review, aspect), averaged over all mentions.
"""
from __future__ import annotations

import html
import re
from dataclasses import dataclass, field

from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer

# ---------------------------------------------------------------------------
# Aspect lexicon: canonical aspect -> surface terms (lower-case, regex-escaped later)
# ---------------------------------------------------------------------------
ASPECT_LEXICON: dict[str, list[str]] = {
    "Camera": ["camera", "cameras", "photo", "photos", "picture quality", "pictures", "pics",
               "selfie", "selfies", "lens", "lenses", "megapixel", "megapixels", "zoom",
               "video recording", "night mode", "portrait mode"],
    "Battery": ["battery", "batteries", "battery life", "charge", "charging", "charger",
                "backup", "mah", "power bank", "fast charging", "standby"],
    "Display": ["display", "screen", "screens", "resolution", "brightness", "amoled", "oled",
                "lcd", "refresh rate", "touchscreen", "touch screen", "panel", "colors", "colours"],
    "Price": ["price", "priced", "pricing", "cost", "costs", "costly", "value", "money",
              "expensive", "cheap", "cheaper", "overpriced", "affordable", "budget", "deal",
              "worth"],
    "Performance": ["performance", "speed", "processor", "chip", "chipset", "ram", "lag",
                    "laggy", "lags", "hangs", "multitasking", "fps", "snappy",
                    "responsive", "slow", "fast", "sluggish", "smooth"],
    "Build Quality": ["build", "build quality", "design", "body", "material", "materials",
                      "plastic", "metal", "glass", "sturdy", "durable", "durability",
                      "flimsy", "finish", "weight", "lightweight", "looks"],
    "Sound": ["sound", "audio", "speaker", "speakers", "volume", "bass", "headphone jack",
              "mic", "microphone", "noise cancellation", "call quality", "earphones"],
    "Software": ["software", "os", "android", "ios", "update", "updates", "ui", "interface",
                 "bloatware", "bugs", "buggy", "firmware"],
    "Storage": ["storage", "memory", "gb", "space", "sd card", "memory card"],
    "Connectivity": ["wifi", "wi-fi", "bluetooth", "network", "signal", "5g", "4g", "lte",
                     "connectivity", "connection", "pairing", "gps", "hotspot"],
    "Heating": ["heat", "heating", "heats", "overheat", "overheats", "overheating", "hot",
                "warm", "temperature", "stays cool", "runs cool"],
    "Delivery & Packaging": ["delivery", "delivered", "shipping", "shipped", "package",
                             "packaging", "box", "arrived", "courier"],
    "Customer Service": ["customer service", "customer support", "support", "warranty",
                         "service center", "replacement", "refund", "return", "seller"],
}

# Domain words VADER does not know (or scores wrongly) for electronics reviews.
DOMAIN_LEXICON: dict[str, float] = {
    "overpriced": -2.4, "laggy": -2.0, "lags": -1.6, "lagging": -1.8, "sluggish": -1.8,
    "hangs": -1.8, "freezes": -2.0, "crashes": -2.0, "buggy": -2.0, "bloatware": -1.5,
    "overheats": -2.2, "overheating": -2.2, "drains": -1.6, "flimsy": -2.0, "grainy": -1.8,
    "blurry": -1.8, "dim": -1.2, "noisy": -1.4, "muffled": -1.6, "tinny": -1.4,
    "defective": -2.5, "faulty": -2.3, "dead": -2.0, "broke": -2.0, "broken": -2.2,
    "scratches": -1.2, "disconnects": -1.8, "disconnecting": -1.8, "disconnected": -1.5, "unresponsive": -2.0, "pricey": -1.3, "costly": -1.2,
    "crisp": 1.8, "vibrant": 2.0, "snappy": 1.8, "smooth": 1.6, "sturdy": 1.8,
    "durable": 1.8, "premium": 1.6, "sleek": 1.6, "sharp": 1.4, "loud": 0.8,
    "affordable": 1.6, "responsive": 1.6, "seamless": 1.8, "stunning": 2.6,
    "flawless": 2.8, "lightweight": 1.0, "fast": 1.2, "slow": -1.5, "cheap": -0.6,
    "bright": 1.2, "clear": 1.4, "decent": 1.2, "mediocre": -1.2, "average": -0.2, "worth": 1.4, "solid": 1.4, "ok": 0.4, "okay": 0.4,
}

# Aspect-specific opinion phrases that general lexicons get wrong ("drains fast" is bad
# even though "fast" is positive). (regex, score) pairs, matched on the clause.
ASPECT_RULES: dict[str, list[tuple[str, float]]] = {
    "Battery": [
        (r"\bdrain(s|ed|ing)?\b.*\b(fast|quick(ly)?|rapid(ly)?|soon)\b", -0.75),
        (r"\b(does ?n[o']?t|did ?n[o']?t|won'?t|not) (last|hold)", -0.7),
        (r"\b(lasts?|lasted|goes|went) (all day|a full day|long|forever|\d+\+? (hours?|days?))", 0.7),
        (r"\b(charges?|charging) (fast|quick(ly)?|rapidly)\b", 0.6),
        (r"\b(charges?|charging) (slow(ly)?)\b", -0.6),
        (r"\bdies\b|\bdied\b", -0.6),
    ],
    "Price": [
        (r"\bover ?priced\b|\btoo (expensive|costly|pricey)\b|\brip[- ]?off\b", -0.75),
        (r"\b(not|n'?t) worth\b", -0.7),
        (r"\bworth (it|every|the)\b|\bvalue for money\b|\bgood value\b|\bgreat value\b|\bbang for (the|your) buck\b", 0.7),
        (r"\b(reasonabl[ey]|fairly|well) priced\b|\baffordable\b", 0.6),
        (r"\bexpensive\b", -0.45),
        (r"\bcheap(er)?\b(?!.*\b(feel|feels|look|looks|build|plastic)\b)", 0.35),
    ],
    "Performance": [
        (r"\b(super|very|really|blazing(ly)?) fast\b|\bno lag\b|\blag[- ]free\b|\bwithout (any )?lag\b", 0.7),
        (r"\b(lags?|lagging|hangs?|freez(es|ing)|stutter(s|ing)?)\b", -0.65),
        (r"\bslow(s down)?\b", -0.55),
    ],
    "Heating": [
        (r"\b(heats? up|gets? (very |really |too )?(hot|warm)|over ?heat(s|ing)?)\b", -0.7),
        (r"\b(does ?n[o']?t|never|no) (heat|overheat|get hot)\b|\bstays? cool\b", 0.6),
    ],
    "Build Quality": [
        (r"\b(feels?|looks?) cheap\b|\bcheap plastic\b|\bcheaply made\b", -0.7),
        (r"\b(feels?|looks?) premium\b|\bwell (built|made)\b", 0.7),
    ],
    "Display": [
        (r"\bhard to (see|read)\b|\bwashed out\b", -0.6),
    ],
    "Connectivity": [
        (r"\b(dis ?connect(s|ed|ing)?|drop(s|ped|ping)?( out)?|keeps? (dropping|cutting)|cuts? out|weak signal|no signal)\b", -0.7),
        (r"\b(can ?not|won'?t|does ?n[o']?t|unable to) (connect|pair)\b", -0.75),
        (r"\b(pairs?|connects?) (instantly|quickly|easily|seamlessly)\b|\bstable (connection|signal)\b", 0.65),
    ],
    "Delivery & Packaging": [
        (r"\b(arrived|delivered) (late|damaged|broken)\b|\bdamaged\b", -0.7),
        (r"\b(arrived|delivered) (early|on time|quickly|fast)\b", 0.6),
    ],
}

CONTRACTIONS = {
    "won't": "will not", "can't": "can not", "n't": " not", "'re": " are", "'s": " is",
    "'d": " would", "'ll": " will", "'ve": " have", "'m": " am",
}

CONTRAST_SPLIT = re.compile(
    r"\s*(?:,|;|:)?\s*\b(?:but|although|though|however|whereas|yet|except|"
    r"on the other hand|unfortunately|sadly)\b\s*,?\s*",
    flags=re.IGNORECASE,
)
SENTENCE_SPLIT = re.compile(r"(?<=[.!?])\s+|\n+")
PRONOUN_START = re.compile(r"^(it|its|it's|this|that|they|these|those|which)\b", re.IGNORECASE)

URL_RE = re.compile(r"https?://\S+|www\.\S+")
TAG_RE = re.compile(r"<[^>]+>")
NON_TEXT_RE = re.compile(r"[^a-zA-Z0-9\s.,!?;:'\-%$+]")
ELONGATION_RE = re.compile(r"(.)\1{2,}")


@dataclass
class AspectMention:
    aspect: str
    term: str
    clause: str
    score: float


@dataclass
class ReviewAnalysis:
    clean_text: str
    overall_score: float
    overall_sentiment: str
    aspects: list[dict] = field(default_factory=list)


def label(score: float, pos: float = 0.05, neg: float = -0.05) -> str:
    if score >= pos:
        return "POSITIVE"
    if score <= neg:
        return "NEGATIVE"
    return "NEUTRAL"


def clean_text(text: str) -> str:
    """Module 3 - Review processing: make raw review text safe for analysis."""
    if not text:
        return ""
    t = html.unescape(str(text))
    t = TAG_RE.sub(" ", t)
    t = URL_RE.sub(" ", t)
    t = t.replace("’", "'").replace("‘", "'").replace("“", '"').replace("”", '"')
    t = NON_TEXT_RE.sub(" ", t)
    t = ELONGATION_RE.sub(r"\1\1", t)           # "sooooo" -> "soo"
    t = re.sub(r"\s+", " ", t).strip()
    return t


def expand_contractions(text: str) -> str:
    t = text
    for k, v in CONTRACTIONS.items():
        t = re.sub(re.escape(k), v, t, flags=re.IGNORECASE)
    return t


def split_clauses(text: str) -> list[str]:
    clauses: list[str] = []
    for sentence in SENTENCE_SPLIT.split(text):
        sentence = sentence.strip()
        if not sentence:
            continue
        for part in CONTRAST_SPLIT.split(sentence):
            # also split on ", and" / commas that separate independent opinion units
            pieces = re.split(r"(?:,\s*(?:and\s+)?|\s+and\s+)(?=(?:the|its|my|this|it|battery|camera|screen|display|price|performance|sound|audio|speakers?|build|design|charging|software|bluetooth|delivery|storage)\b)",
                              part, flags=re.IGNORECASE)
            carry = ""
            for sub in pieces:
                sub = (carry + " and " + sub if carry else sub).strip(" ,.;:!?")
                # "The camera and display are great": a fragment without its own opinion
                # (fewer than 3 words) is merged forward so both aspects share the opinion.
                if len(sub.split()) < 3:
                    carry = sub
                    continue
                carry = ""
                clauses.append(sub)
            if carry and len(carry) > 1:
                clauses.append(carry)
    return clauses


class AbsaEngine:
    def __init__(self) -> None:
        self.vader = SentimentIntensityAnalyzer()
        self.vader.lexicon.update(DOMAIN_LEXICON)
        # Compile aspect patterns, longest terms first so "battery life" wins over "battery".
        self.aspect_patterns: list[tuple[str, str, re.Pattern]] = []
        for aspect, terms in ASPECT_LEXICON.items():
            for term in terms:
                pat = re.compile(r"\b" + re.escape(term).replace(r"\ ", r"\s+") + r"\b", re.IGNORECASE)
                self.aspect_patterns.append((aspect, term, pat))
        self.aspect_patterns.sort(key=lambda x: -len(x[1]))
        self.rules = {a: [(re.compile(p, re.IGNORECASE), s) for p, s in rs] for a, rs in ASPECT_RULES.items()}

    # -- aspect extraction ---------------------------------------------------
    def find_aspects(self, clause: str) -> list[tuple[str, str]]:
        found: dict[str, str] = {}
        taken: list[tuple[int, int]] = []
        for aspect, term, pat in self.aspect_patterns:
            for m in pat.finditer(clause):
                if any(m.start() < e and m.end() > s for s, e in taken):
                    continue
                taken.append((m.start(), m.end()))
                found.setdefault(aspect, term)
        # "feels cheap" is a build-quality complaint, not a price opinion
        if "Build Quality" in found and found.get("Price", "").startswith("cheap"):
            found.pop("Price")
        return list(found.items())

    # -- sentiment -----------------------------------------------------------
    def clause_score(self, clause: str, aspect: str | None = None) -> float:
        base = self.vader.polarity_scores(clause)["compound"]
        if aspect and aspect in self.rules:
            for pat, s in self.rules[aspect]:
                if pat.search(clause):
                    # rule evidence dominates when the general lexicon disagrees or is neutral
                    if abs(base) < 0.3 or (base > 0) != (s > 0):
                        return round(s * 0.7 + base * 0.3, 4)
                    return round((base + s) / 2, 4)
        return round(base, 4)

    # -- full review -----------------------------------------------------------
    def analyze(self, text: str, rating: float | None = None) -> ReviewAnalysis:
        cleaned = clean_text(text)
        expanded = expand_contractions(cleaned)
        overall = self.vader.polarity_scores(expanded)["compound"] if expanded else 0.0
        if rating is not None and 1 <= rating <= 5:
            # blend star rating (mapped to [-1, 1]) with text polarity for the overall label
            overall = round(0.6 * overall + 0.4 * ((rating - 3) / 2), 4)

        mentions: list[AspectMention] = []
        prev_aspects: list[tuple[str, str]] = []
        for clause in split_clauses(expanded):
            aspects = self.find_aspects(clause)
            if not aspects and prev_aspects and PRONOUN_START.match(clause):
                aspects = prev_aspects          # "Battery is decent. It lasts a day."
            for aspect, term in aspects:
                mentions.append(AspectMention(aspect, term, clause, self.clause_score(clause, aspect)))
            if aspects:
                prev_aspects = aspects

        grouped: dict[str, list[AspectMention]] = {}
        for m in mentions:
            grouped.setdefault(m.aspect, []).append(m)
        aspects_out = []
        for aspect, ms in grouped.items():
            score = round(sum(m.score for m in ms) / len(ms), 4)
            strongest = max(ms, key=lambda m: abs(m.score))
            aspects_out.append({
                "aspect": aspect,
                "term": strongest.term,
                "sentiment": label(score),
                "score": score,
                "snippet": strongest.clause[:500],
                "mentions": len(ms),
            })
        return ReviewAnalysis(cleaned, round(overall, 4), label(overall), aspects_out)

    def opinion_clauses(self, text: str) -> list[tuple[str, float]]:
        """Clauses with their polarity - used by keyword/concern mining."""
        return [(c, self.vader.polarity_scores(c)["compound"])
                for c in split_clauses(expand_contractions(clean_text(text)))]
