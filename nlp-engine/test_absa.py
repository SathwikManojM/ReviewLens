"""Run with:  python -m pytest -q   (from the nlp-engine folder)"""
from absa import AbsaEngine, clean_text
from keywords import top_keywords

engine = AbsaEngine()


def aspects(text):
    return {a["aspect"]: a["sentiment"] for a in engine.analyze(text).aspects}


def test_project_brief_example():
    text = ("The camera quality is excellent, but the battery drains quickly. "
            "The display is beautiful, although the phone is overpriced.")
    assert aspects(text) == {"Camera": "POSITIVE", "Battery": "NEGATIVE",
                             "Display": "POSITIVE", "Price": "NEGATIVE"}


def test_shared_opinion_applies_to_all_aspects():
    assert aspects("The camera and display are great.") == {"Camera": "POSITIVE", "Display": "POSITIVE"}


def test_pronoun_inherits_previous_aspect():
    assert aspects("Battery is decent. It lasts all day with heavy use.")["Battery"] == "POSITIVE"


def test_negation_and_domain_rules():
    r = aspects("Not worth the money. Phone heats up while gaming. Super fast performance, no lag at all!")
    assert r["Price"] == "NEGATIVE"
    assert r["Heating"] == "NEGATIVE"
    assert r["Performance"] == "POSITIVE"


def test_feels_cheap_is_build_quality_not_price():
    r = aspects("Feels cheap and plastic.")
    assert r == {"Build Quality": "NEGATIVE"}


def test_cleaning_removes_html_and_urls():
    assert clean_text("Great <br/>phone!!! visit https://x.y sooooo good") == "Great phone!! visit soo good"


def test_keywords_rank_repeated_terms():
    kws = [k["term"] for k in top_keywords(["battery life is poor", "poor battery life", "great camera"] * 5)]
    assert "battery life" in kws


def test_connectivity_failures_are_negative():
    assert aspects("Bluetooth keeps disconnecting.") == {"Connectivity": "NEGATIVE"}
    assert aspects("The connection drops constantly.") == {"Connectivity": "NEGATIVE"}
    assert aspects("Bluetooth pairing is seamless.") == {"Connectivity": "POSITIVE"}
