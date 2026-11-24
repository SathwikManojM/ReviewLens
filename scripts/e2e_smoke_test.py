"""
ReviewLens end-to-end smoke test against a running stack (backend :8080, NLP engine :8000, MongoDB).

    python scripts/e2e_smoke_test.py                     # uses ADMIN_EMAIL / ADMIN_PASSWORD from .env
    python scripts/e2e_smoke_test.py --base http://localhost:8080 --keep

It registers a throwaway user, uploads sample-data/sample_electronics_reviews.csv, verifies every user-facing
API (upload, processing, dashboard, aspects, comparison, review search, analyzer, report library) and the
admin API, checks that a normal user is refused by the admin API, then deletes the test dataset.
Requires: pip install requests
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import uuid
from datetime import date, timedelta
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
EXAMPLE = ("The camera quality is excellent, but the battery drains quickly. "
           "The display is beautiful, although the phone is overpriced.")
results: list[tuple[str, bool]] = []


def check(name: str, cond, detail: str = "") -> bool:
    ok = bool(cond)
    results.append((name, ok))
    print(("  PASS  " if ok else "  FAIL  ") + name + (f"   <- {detail}" if detail and not ok else ""))
    return ok


def env_values() -> dict:
    vals = {}
    p = ROOT / ".env"
    if p.exists():
        for line in p.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.strip().startswith("#"):
                k, v = line.split("=", 1)
                vals[k.strip()] = v.strip()
    return vals


def poll(fn, done, timeout=180):
    end = time.time() + timeout
    value = fn()
    while not done(value) and time.time() < end:
        time.sleep(1)
        value = fn()
    return value


def main() -> int:
    env = env_values()
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--admin-email", default=env.get("ADMIN_EMAIL"))
    ap.add_argument("--admin-password", default=env.get("ADMIN_PASSWORD"))
    ap.add_argument("--keep", action="store_true", help="keep the uploaded test dataset")
    a = ap.parse_args()
    B = a.base.rstrip("/") + "/api"
    s = requests.Session()

    print("\n[1] Platform")
    h = s.get(B + "/health").json()
    check("backend, MongoDB and NLP engine are UP", h.get("backend") == "UP" and h.get("database") == "UP" and h.get("nlpEngine") == "UP", h)
    st = s.get(B + "/public/status").json()
    check("public status exposes feature flags and limits", "features" in st and "limits" in st)
    demo = s.post(B + "/public/demo-analyze", json={"text": EXAMPLE})
    check("landing-page demo runs the real NLP engine", demo.ok and len(demo.json()["result"]["aspects"]) == 4, demo.text[:200])

    print("\n[2] Authentication")
    email, pw = f"smoke-{uuid.uuid4().hex[:8]}@example.com", "Smoke-test-123"
    r = s.post(B + "/auth/register", json={"name": "Smoke Tester", "email": email, "password": pw})
    check("register returns 201", r.status_code == 201, r.text[:200])
    check("auth response contains no password hash", "passwordHash" not in r.text and "$2a$" not in r.text)
    check("weak password rejected", s.post(B + "/auth/register", json={"name": "x", "email": "w" + email, "password": "short"}).status_code == 400)
    r = s.post(B + "/auth/login", json={"email": email, "password": pw})
    check("login returns a token", r.ok and r.json().get("token"))
    token = r.json()["token"]
    H = {"Authorization": f"Bearer {token}"}
    check("wrong password -> 401", s.post(B + "/auth/login", json={"email": email, "password": "Wrong-pass-1"}).status_code == 401)
    check("no token -> 401", s.get(B + "/datasets").status_code == 401)
    check("/auth/me returns USER role", s.get(B + "/auth/me", headers=H).json().get("role") == "USER")

    print("\n[3] Dataset upload & processing")
    sample = (ROOT / "sample-data" / "sample_electronics_reviews.csv").read_bytes()
    r = s.post(B + "/datasets/preview", headers=H, files={"file": ("sample.csv", sample[:65536], "text/csv")})
    check("preview detects columns and mapping", r.ok and r.json()["detectedMapping"].get("text") == "reviews.text", r.text[:200])
    mapping = r.json()["detectedMapping"]
    r = s.post(B + "/datasets", headers=H, data={"name": "Smoke test dataset"}, files={
        "file": ("sample_electronics_reviews.csv", sample, "text/csv"),
        "mapping": ("mapping.json", json.dumps(mapping), "application/json")})
    check("upload returns 201 with a queued job", r.status_code == 201 and r.json()["latestJob"]["status"] == "QUEUED", r.text[:300])
    dsid = r.json()["id"]
    d = poll(lambda: s.get(B + f"/datasets/{dsid}", headers=H).json(), lambda v: v["status"] in ("COMPLETED", "FAILED"))
    check("dataset processed (450 reviews, 3 products)", d["status"] == "COMPLETED" and d["processedReviews"] == 450 and d["productCount"] == 3, json.dumps(d)[:300])
    jobs = s.get(B + f"/datasets/{dsid}/jobs", headers=H).json()
    check("analysis job recorded with duration and engine", jobs and jobs[0]["status"] == "COMPLETED" and jobs[0]["durationMs"] and jobs[0]["nlpEngine"])
    prods = s.get(B + f"/datasets/{dsid}/products", headers=H).json()
    check("products listed with review counts", len(prods) == 3 and all(p["reviewCount"] == 150 for p in prods))

    print("\n[4] Analytics")
    dash = s.get(B + "/analytics/dashboard", headers=H, params={"datasetId": dsid}).json()
    check("dashboard totals", dash["totalReviews"] == 450 and dash["productsAnalyzed"] == 3, str(dash.get("totalReviews")))
    check("dashboard aspects, insight and trend", len(dash["aspects"]) >= 6 and dash["insight"].startswith("Customers") and len(dash["trend"]) >= 6)
    check("dashboard keywords, praise and concerns", dash["keywords"] and dash["praise"] and dash["concerns"])
    lo = date.fromisoformat(dash["minDate"])
    dated = s.get(B + "/analytics/dashboard", headers=H, params={"datasetId": dsid, "from": str(lo), "to": str(lo + timedelta(days=60))}).json()
    check("date filter narrows the scope", 0 < dated["totalReviews"] < 450, str(dated.get("totalReviews")))
    ov = s.get(B + "/analytics/aspects", headers=H, params={"datasetId": dsid}).json()
    check("aspect overview includes the six core aspects", all(c in [x["aspect"] for x in ov["aspects"]] for c in ov["coreAspects"]))
    det = s.get(B + "/analytics/aspects/detail", headers=H, params={"datasetId": dsid, "aspect": "Battery"}).json()
    check("aspect detail has examples, terms and products", det["positiveExamples"] and det["negativeExamples"] and det["terms"] and len(det["products"]) == 3)
    cmp = s.get(B + "/analytics/compare", headers=H, params={"datasetId": dsid, "productIds": f"{prods[0]['id']},{prods[1]['id']}"}).json()
    check("comparison with winners, strengths and summary", cmp["winners"] and cmp["products"][0]["strengths"] is not None and "leads on" in cmp["summary"], cmp.get("summary"))
    check("comparison of 1 product rejected", s.get(B + "/analytics/compare", headers=H, params={"datasetId": dsid, "productIds": prods[0]["id"]}).status_code == 400)

    print("\n[5] Review explorer")
    rv = s.get(B + f"/datasets/{dsid}/reviews", headers=H, params={"aspect": "Battery", "sentiment": "NEGATIVE", "size": 10}).json()
    check("aspect + sentiment filter", rv["totalElements"] > 0 and all(any(x["aspect"] == "Battery" and x["sentiment"] == "NEGATIVE" for x in r["aspects"]) for r in rv["content"]))
    rv = s.get(B + f"/datasets/{dsid}/reviews", headers=H, params={"rating": 5, "size": 10}).json()
    check("rating filter", rv["totalElements"] > 0 and all(r["rating"] == 5 for r in rv["content"]))
    rv = s.get(B + f"/datasets/{dsid}/reviews", headers=H, params={"q": "night mode", "size": 10}).json()
    check("text search", rv["totalElements"] > 0 and all("night mode" in r["text"].lower() for r in rv["content"]))
    p2 = s.get(B + f"/datasets/{dsid}/reviews", headers=H, params={"page": 1, "size": 20}).json()
    check("pagination", p2["page"] == 1 and p2["totalPages"] == 23 and len(p2["content"]) == 20)

    print("\n[6] Live analyzer")
    an = s.post(B + "/analyze", headers=H, json={"text": EXAMPLE}).json()
    got = {x["aspect"]: x["sentiment"] for x in an["result"]["aspects"]}
    check("brief example -> Camera+, Battery-, Display+, Price-", got == {"Camera": "POSITIVE", "Battery": "NEGATIVE", "Display": "POSITIVE", "Price": "NEGATIVE"}, got)
    check("result labelled with the baseline engine", "rule-based" in an.get("engine", ""))

    print("\n[7] Report library")
    pdf = s.post(B + "/reports", headers=H, json={"datasetId": dsid, "format": "PDF", "compareProductIds": [prods[0]["id"], prods[1]["id"]]})
    csv = s.post(B + "/reports", headers=H, json={"datasetId": dsid, "format": "CSV"})
    check("report generation accepted (202)", pdf.status_code == 202 and csv.status_code == 202, pdf.text[:200])
    for rep, magic in ((pdf.json(), b"%PDF"), (csv.json(), "﻿review_id".encode())):
        done = poll(lambda: s.get(B + f"/reports/{rep['id']}", headers=H).json(), lambda v: v["status"] != "GENERATING", 60)
        f = s.get(B + f"/reports/{rep['id']}/download", headers=H)
        check(f"{rep['format']} report ready and downloadable", done["status"] == "READY" and f.ok and f.content.startswith(magic), done.get("errorMessage"))
    lib = s.get(B + "/reports", headers=H).json()
    check("report library lists both reports", lib["totalElements"] >= 2)

    print("\n[8] Admin portal API")
    check("USER token -> 403 on /admin/users", s.get(B + "/admin/users", headers=H).status_code == 403)
    check("no token -> 401 on /admin/dashboard", s.get(B + "/admin/dashboard").status_code == 401)
    check("USER cannot use admin sign-in", s.post(B + "/auth/admin/login", json={"email": email, "password": pw}).status_code == 403)
    if not (a.admin_email and a.admin_password):
        check("admin credentials available (ADMIN_EMAIL / ADMIN_PASSWORD)", False, "skipping admin checks")
    else:
        r = s.post(B + "/auth/admin/login", json={"email": a.admin_email, "password": a.admin_password})
        check("admin sign-in", r.ok and r.json()["user"]["role"] == "ADMIN", r.text[:200])
        A = {"Authorization": f"Bearer {r.json().get('token', '')}"}
        dash = s.get(B + "/admin/dashboard", headers=A).json()
        check("admin dashboard counts", dash["users"]["total"] >= 2 and dash["datasets"] >= 1 and dash["reviews"] >= 450)
        check("admin dashboard usage series (daily/weekly/monthly)", len(dash["usage"]["daily"]) == 30 and len(dash["usage"]["weekly"]) == 12 and len(dash["usage"]["monthly"]) == 12)
        users = s.get(B + "/admin/users", headers=A, params={"q": email}).json()
        check("admin user search", users["totalElements"] == 1 and users["content"][0]["datasetCount"] == 1)
        check("admin responses contain no password hashes", "passwordHash" not in json.dumps(users) and "$2a$" not in json.dumps(users))
        uid = users["content"][0]["id"]
        check("admin user profile", s.get(B + f"/admin/users/{uid}", headers=A).json()["user"]["email"] == email)
        health = s.get(B + "/admin/system/health", headers=A).json()
        check("system health: all services UP", all(x["status"] == "UP" for x in health["services"]), [x["status"] for x in health["services"]])
        check("health shows no connection strings", "mongodb://" not in json.dumps(health))
        check("admin dataset list", any(r["dataset"]["id"] == dsid for r in s.get(B + "/admin/datasets", headers=A).json()["content"]))
        check("admin job list", s.get(B + "/admin/analysis-jobs", headers=A).json()["totalElements"] >= 1)
        check("admin product list", s.get(B + "/admin/products", headers=A, params={"datasetId": dsid}).json()["totalElements"] == 3)
        check("admin review quality summary", s.get(B + "/admin/reviews/summary", headers=A, params={"datasetId": dsid}).json()["total"] == 450)
        check("admin analytics", s.get(B + "/admin/analytics", headers=A, params={"range": "12w"}).json()["range"] == "12w")
        exp = s.get(B + "/admin/reports/export", headers=A, params={"format": "pdf"})
        check("admin platform report export (PDF)", exp.ok and exp.content.startswith(b"%PDF"))
        logs = s.get(B + "/admin/audit-logs", headers=A, params={"action": "ADMIN_LOGIN"}).json()
        check("audit log records the admin sign-in", logs["totalElements"] >= 1)
        check("settings readable", "featureFlags" in s.get(B + "/admin/settings", headers=A).json())

    print("\n[9] Cleanup")
    if a.keep:
        print(f"  kept dataset {dsid}")
    else:
        check("delete dataset (cascade)", s.delete(B + f"/datasets/{dsid}", headers=H).status_code == 204)
        check("deleted dataset -> 404", s.get(B + f"/datasets/{dsid}", headers=H).status_code == 404)
    r = s.post(B + "/auth/logout", headers=H)
    check("logout revokes the token", r.status_code == 204 and s.get(B + "/auth/me", headers=H).status_code == 401)

    failed = [n for n, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} checks passed")
    for n in failed:
        print("  failed:", n)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
