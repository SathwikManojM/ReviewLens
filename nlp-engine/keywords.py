"""
ReviewLens - keyword analysis (Module 6).

* Top keywords   : unigrams + bigrams ranked by TF-IDF-weighted frequency across reviews.
* Praise terms   : keywords that occur mostly in positive clauses.
* Concerns       : keywords that occur mostly in negative clauses (customer pain points).
"""
from __future__ import annotations

import math
import re
from collections import Counter

STOPWORDS = set("""
a about above after again against all am an and any are as at be because been before being
below between both but by can could did do does doing down during each few for from further
had has have having he her here hers herself him himself his how i if in into is it its itself
just me more most my myself no nor not now of off on once only or other our ours ourselves out
over own same she should so some such than that the their theirs them themselves then there
these they this those through to too under until up very was we were what when where which while
who whom why will with would you your yours yourself yourselves also get got one two really
would could much even still well product item phone thing things bought buy buying purchase
purchased amazon use used using ive im dont didnt doesnt cant wont isnt wasnt lot little bit
will just like make made makes go going way back day days time times since month months year
years new need want overall first last ever every many though although however yet however review
""".split())

TOKEN_RE = re.compile(r"[a-z][a-z0-9\-]+")


def tokenize(text: str) -> list[str]:
    return [t for t in TOKEN_RE.findall(text.lower()) if t not in STOPWORDS and len(t) > 2]


def ngrams(tokens: list[str]) -> list[str]:
    return tokens + [f"{a} {b}" for a, b in zip(tokens, tokens[1:])]


def top_keywords(texts: list[str], top_n: int = 30) -> list[dict]:
    if not texts:
        return []
    tf: Counter = Counter()
    df: Counter = Counter()
    for t in texts:
        grams = ngrams(tokenize(t))
        tf.update(grams)
        df.update(set(grams))
    n = len(texts)
    scored = []
    for term, freq in tf.items():
        if df[term] < (2 if n >= 20 else 1):
            continue
        idf = math.log((1 + n) / (1 + df[term])) + 1
        bigram_boost = 1.3 if " " in term else 1.0
        scored.append((term, freq, freq * idf * bigram_boost))
    scored.sort(key=lambda x: -x[2])
    out, seen = [], set()
    for term, freq, score in scored:
        # drop a unigram when a stronger bigram containing it was already chosen
        if " " not in term and any(term in s.split() for s in seen if " " in s) and len(out) > 5:
            continue
        seen.add(term)
        out.append({"term": term, "frequency": freq, "score": round(score, 3)})
        if len(out) >= top_n:
            break
    return out


def polarity_keywords(clauses: list[tuple[str, float]], top_n: int = 20) -> tuple[list[dict], list[dict]]:
    """Split clauses by polarity and return (praise_terms, concern_terms)."""
    pos_counts: Counter = Counter()
    neg_counts: Counter = Counter()
    for clause, score in clauses:
        grams = set(ngrams(tokenize(clause)))
        if score >= 0.2:
            pos_counts.update(grams)
        elif score <= -0.2:
            neg_counts.update(grams)

    def rank(primary: Counter, other: Counter) -> list[dict]:
        rows = []
        for term, c in primary.items():
            if c < 2:
                continue
            ratio = (c + 1) / (other.get(term, 0) + 1)
            if ratio < 1.5:
                continue
            boost = 1.3 if " " in term else 1.0
            rows.append({"term": term, "frequency": c, "score": round(c * math.log1p(ratio) * boost, 3)})
        rows.sort(key=lambda r: -r["score"])
        return rows[:top_n]

    return rank(pos_counts, neg_counts), rank(neg_counts, pos_counts)
