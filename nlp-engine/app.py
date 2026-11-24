"""
ReviewLens NLP engine - FastAPI service consumed by the Spring Boot backend.

Run:  uvicorn app:app --port 8000
Docs: http://localhost:8000/docs
"""
from __future__ import annotations

from fastapi import FastAPI
from pydantic import BaseModel, Field

from absa import ASPECT_LEXICON, AbsaEngine
from keywords import polarity_keywords, top_keywords

app = FastAPI(title="ReviewLens NLP Engine", version="1.1.0")
engine = AbsaEngine()

# Identifies the model that produced a result. Spring Boot stores this label on every analysis job and
# dataset, so baseline (rule-based) results can always be told apart from a future transformer model.
ENGINE = {
    "name": "rule-based-vader",
    "version": "1.1.0",
    "type": "baseline",
    "description": "Lexicon + rules baseline: clause segmentation, aspect lexicon, VADER with electronics domain rules",
}


class ReviewIn(BaseModel):
    id: int | str
    text: str = ""
    rating: float | None = None


class AnalyzeRequest(BaseModel):
    reviews: list[ReviewIn] = Field(default_factory=list)


class AspectOut(BaseModel):
    aspect: str
    term: str
    sentiment: str
    score: float
    snippet: str
    mentions: int


class ReviewOut(BaseModel):
    id: int | str
    cleanText: str
    overallSentiment: str
    overallScore: float
    aspects: list[AspectOut]


class AnalyzeResponse(BaseModel):
    results: list[ReviewOut]
    engine: dict


class KeywordRequest(BaseModel):
    texts: list[str] = Field(default_factory=list)
    topN: int = 30


class Keyword(BaseModel):
    term: str
    frequency: int
    score: float


class KeywordResponse(BaseModel):
    keywords: list[Keyword]
    praise: list[Keyword]
    concerns: list[Keyword]


@app.get("/health")
def health():
    return {"status": "UP", "aspects": list(ASPECT_LEXICON.keys()), "engine": ENGINE}


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze(req: AnalyzeRequest):
    """Modules 3-5: clean each review, extract aspects, classify per-aspect sentiment."""
    results = []
    for r in req.reviews:
        a = engine.analyze(r.text, r.rating)
        results.append(ReviewOut(id=r.id, cleanText=a.clean_text, overallSentiment=a.overall_sentiment,
                                 overallScore=a.overall_score, aspects=[AspectOut(**x) for x in a.aspects]))
    return AnalyzeResponse(results=results, engine=ENGINE)


@app.post("/keywords", response_model=KeywordResponse)
def keywords(req: KeywordRequest):
    """Module 6: frequent keywords, praise terms and customer concerns."""
    texts = [t for t in req.texts if t]
    clauses = [c for t in texts for c in engine.opinion_clauses(t)]
    praise, concerns = polarity_keywords(clauses, top_n=min(req.topN, 25))
    return KeywordResponse(keywords=top_keywords(texts, req.topN), praise=praise, concerns=concerns)
