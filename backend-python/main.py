import json
import os
from decimal import Decimal, ROUND_FLOOR, ROUND_HALF_UP
from typing import Annotated, Any
import time
from mangum import Mangum
from firebase_functions import https_fn

import firebase_admin
import stripe
from fastapi import FastAPI, Header, HTTPException
from firebase_admin import auth, credentials, db
from pydantic import BaseModel

app = FastAPI()

WEIGHTS = {
    "social": 2.0,
    "stream": 1.5,
    "neutral": 1.0,
    "productive": 0.5,
}
MIN_MULTIPLIER = 0.5
MAX_MULTIPLIER = 1.5
SCORE_MAX_AGE_MS = 24 * 60 * 60 * 1000


class CreatePaymentIntentRequest(BaseModel):
    crewId: str


class AppUsage(BaseModel):
    appName: str
    category: str
    minutes: float


class UsageSyncBody(BaseModel):
    userId: str
    reportDate: str
    apps: list[AppUsage]
    source: str = "android"


def get_database_root() -> Any:
    if not firebase_admin._apps:
        database_url = os.environ.get("FIREBASE_DATABASE_URL")
        if not database_url:
            raise HTTPException(status_code=503, detail="Firebase server configuration is missing")

        service_account_json = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON")
        if service_account_json:
            credential = credentials.Certificate(json.loads(service_account_json))
        else:
            credential = credentials.ApplicationDefault()
        firebase_admin.initialize_app(credential, {"databaseURL": database_url})
    return db.reference("/")


def multipliers_for(member_count: int) -> list[float]:
    if member_count <= 0:
        return []
    if member_count == 1:
        return [1.0]

    anchors = [0.5, 0.8, 1.2, 1.5]
    values = []
    for rank_index in range(member_count):
        scaled_rank = rank_index * 3.0 / (member_count - 1)
        segment = min(int(scaled_rank), 2)
        position = scaled_rank - segment
        values.append(anchors[segment] + (anchors[segment + 1] - anchors[segment]) * position)
    return values


def apply_multiplier_reductions(baseline: list[float], requested: list[float]) -> list[float]:
    if len(baseline) != len(requested):
        raise ValueError("Multiplier reductions must match crew membership")

    values = baseline.copy()
    for member_index, amount in enumerate(requested):
        reduction = min(max(amount, 0.0), max(values[member_index] - MIN_MULTIPLIER, 0.0))
        if reduction <= 0 or len(values) == 1:
            continue

        recipients = [index for index in range(len(values)) if index != member_index]
        available = sum(max(MAX_MULTIPLIER - values[index], 0.0) for index in recipients)
        reduction = min(reduction, available)
        if reduction <= 0:
            continue

        values[member_index] -= reduction
        remaining = reduction
        active = [index for index in recipients if values[index] < MAX_MULTIPLIER]
        while remaining > 1e-9 and active:
            capacity = sum(MAX_MULTIPLIER - values[index] for index in active)
            if capacity <= 0:
                break
            distributed_target = min(remaining, capacity)
            distributed = 0.0
            for position, index in enumerate(active):
                amount = (
                    distributed_target - distributed
                    if position == len(active) - 1
                    else distributed_target * (MAX_MULTIPLIER - values[index]) / capacity
                )
                applied = min(amount, MAX_MULTIPLIER - values[index])
                values[index] += applied
                distributed += applied
            remaining -= distributed
            active = [index for index in active if values[index] < MAX_MULTIPLIER - 1e-9]
    return values


def allocate_minor_units(total_minor: int, multipliers: list[float]) -> list[int]:
    if not multipliers or sum(multipliers) <= 0:
        raise HTTPException(status_code=409, detail="Crew has no payable member shares")

    denominator = sum(Decimal(str(value)) for value in multipliers)
    exact = [Decimal(total_minor) * Decimal(str(value)) / denominator for value in multipliers]
    shares = [int(value.to_integral_value(rounding=ROUND_FLOOR)) for value in exact]
    remaining = total_minor - sum(shares)
    order = sorted(
        range(len(exact)),
        key=lambda index: (exact[index] - shares[index], -index),
        reverse=True,
    )
    for index in order[:remaining]:
        shares[index] += 1
    return shares


@app.get("/")
def read_root() -> dict[str, str]:
    return {"message": "ByteShare Python Backend Active"}


@app.post(
    "/payments/create-intent",
    responses={
        401: {"description": "Missing or invalid Firebase authentication"},
        403: {"description": "The authenticated user is not a member of the crew"},
        404: {"description": "Crew does not exist"},
        409: {"description": "Usage is stale or the crew share cannot be paid"},
        422: {"description": "Invalid crew ID"},
        502: {"description": "Stripe failed to create the PaymentIntent"},
        503: {"description": "Firebase or Stripe server configuration is missing"},
    },
)
def create_payment_intent(
    body: CreatePaymentIntentRequest,
    authorization: Annotated[str | None, Header()] = None,
) -> dict[str, Any]:
    if not body.crewId or "/" in body.crewId or "." in body.crewId:
        raise HTTPException(status_code=422, detail="Invalid crew ID")
    stripe_secret_key = os.environ.get("STRIPE_SECRET_KEY")
    stripe_publishable_key = os.environ.get("STRIPE_PUBLISHABLE_KEY")
    if not stripe_secret_key or not stripe_publishable_key:
        raise HTTPException(status_code=503, detail="Stripe server configuration is missing")
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Firebase sign-in is required")

    try:
        firebase_user = auth.verify_id_token(authorization.removeprefix("Bearer "))
    except Exception as error:
        raise HTTPException(status_code=401, detail="Firebase token is invalid") from error

    uid = firebase_user["uid"]
    root = get_database_root()
    crew = root.child("crews").child(body.crewId).get()
    if not isinstance(crew, dict) or not isinstance(crew.get("members"), dict):
        raise HTTPException(status_code=404, detail="Crew not found")
    members = crew["members"]
    if uid not in members:
        raise HTTPException(status_code=403, detail="You are not a member of this crew")

    member_ids = sorted(members.keys())
    scores: dict[str, float] = {}
    now_ms = int(time.time() * 1000)
    for member_id in member_ids:
        snapshot = root.child("usageRolling").child(member_id).get()
        if not isinstance(snapshot, dict):
            raise HTTPException(status_code=409, detail="A crew member has not synced seven-day usage")
        updated_at = int(snapshot.get("updatedAt", 0))
        if updated_at <= 0 or now_ms - updated_at > SCORE_MAX_AGE_MS:
            raise HTTPException(status_code=409, detail="A crew member's seven-day usage is stale")
        scores[member_id] = sum(
            float(snapshot.get(category, 0.0)) * weight
            for category, weight in WEIGHTS.items()
        )

    ranked_ids = sorted(member_ids, key=lambda member_id: (scores[member_id], member_id))
    baseline = multipliers_for(len(ranked_ids))
    reductions_by_member = crew.get("multiplierReductions", {})
    reductions = []
    for member_id in ranked_ids:
        member_reductions = reductions_by_member.get(member_id, {})
        reductions.append(sum(float(value) for value in member_reductions.values()))
    final_multipliers = apply_multiplier_reductions(baseline, reductions)

    total_bill = Decimal(str(crew.get("totalBill", 0)))
    total_minor = int((total_bill * 100).quantize(Decimal("1"), rounding=ROUND_HALF_UP))
    if total_minor <= 0:
        raise HTTPException(status_code=409, detail="Crew bill amount is invalid")
    shares = allocate_minor_units(total_minor, final_multipliers)
    rank_index = ranked_ids.index(uid)
    amount_minor = shares[rank_index]
    if amount_minor < 50:
        raise HTTPException(status_code=409, detail="Calculated share is below Stripe's minimum")

    stripe.api_key = stripe_secret_key
    try:
        intent = stripe.PaymentIntent.create(
            amount=amount_minor,
            currency="inr",
            automatic_payment_methods={"enabled": True},
            metadata={"firebaseUid": uid, "crewId": body.crewId},
        )
    except Exception as error:
        raise HTTPException(status_code=502, detail="Stripe could not create the payment") from error

    return {
        "clientSecret": intent.client_secret,
        "publishableKey": stripe_publishable_key,
        "amountMinor": amount_minor,
        "multiplier": final_multipliers[rank_index],
        "rank": rank_index + 1,
    }


@app.post("/usage/sync")
def sync_usage(body: UsageSyncBody) -> dict[str, str]:
    return {"status": "accepted", "userId": body.userId}


@app.get("/group/{group_id}/ranks")
def get_group_ranks(group_id: str) -> dict[str, Any]:
    return {"groupId": group_id, "ranks": []}


handler = Mangum(app)


@https_fn.on_request()
def api(req: https_fn.Request) -> https_fn.Response:
    return handler(req)