import html
import json
import os
from decimal import Decimal, ROUND_FLOOR, ROUND_HALF_UP
from typing import Annotated, Any
import time
from mangum import Mangum
from firebase_functions import https_fn

import firebase_admin
import stripe
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import HTMLResponse
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

    # Ensure Firebase is initialized FIRST before verifying the token
    root = get_database_root()

    try:
        firebase_user = auth.verify_id_token(authorization.removeprefix("Bearer "))
    except Exception as error:
        raise HTTPException(status_code=401, detail="Firebase token is invalid") from error

    uid = firebase_user["uid"]
    crew = root.child("crews").child(body.crewId).get()
    if not isinstance(crew, dict) or not isinstance(crew.get("members"), dict):
        raise HTTPException(status_code=404, detail="Crew not found")
    members = crew["members"]
    if uid not in members:
        raise HTTPException(status_code=403, detail="You are not a member of this crew")
    if (crew.get("paidMembers") or {}).get(uid) is True:
        raise HTTPException(status_code=409, detail="You have already paid your share for this crew")

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


@app.post(
    "/payments/webhook",
    responses={
        400: {"description": "Invalid payload or Stripe signature"},
        503: {"description": "Stripe webhook secret is not configured"},
    },
)
async def stripe_webhook(
    request: Request,
    stripe_signature: Annotated[str | None, Header()] = None,
) -> dict[str, str]:
    """Marks a crew share as paid only after Stripe confirms the PaymentIntent succeeded."""
    webhook_secret = os.environ.get("STRIPE_WEBHOOK_SECRET")
    if not webhook_secret:
        raise HTTPException(status_code=503, detail="Stripe webhook configuration is missing")
    payload = await request.body()
    try:
        event = stripe.Webhook.construct_event(payload, stripe_signature or "", webhook_secret)
    except (ValueError, stripe.SignatureVerificationError) as error:
        raise HTTPException(status_code=400, detail="Invalid Stripe webhook") from error

    if event["type"] == "payment_intent.succeeded":
        intent = event["data"]["object"]
        metadata = intent.get("metadata") or {}
        uid = metadata.get("firebaseUid")
        crew_id = metadata.get("crewId")
        if uid and crew_id:
            root = get_database_root()
            crew_ref = root.child("crews").child(crew_id)
            if crew_ref.child("members").child(uid).get() is True:
                crew_ref.child("paidMembers").child(uid).set(True)
                root.child("payments").child(crew_id).child(uid).child(intent["id"]).set({
                    "amountMinor": intent.get("amount_received", intent.get("amount")),
                    "currency": intent.get("currency"),
                    "paidAt": int(time.time() * 1000),
                })
    return {"status": "ok"}


@app.post("/usage/sync")
def sync_usage(body: UsageSyncBody) -> dict[str, str]:
    return {"status": "accepted", "userId": body.userId}


@app.get("/group/{group_id}/ranks")
def get_group_ranks(group_id: str) -> dict[str, Any]:
    return {"groupId": group_id, "ranks": []}


# ---------- Account deletion (Google Play user-data policy) ----------

# Per-user nodes keyed by Firebase uid
USER_OWNED_NODES = (
    "users", "usageDaily", "usageRolling", "xp", "challengeProgress",
    "userCrews", "nudgesReceived", "friends",
)
# Per-member entries inside crews/{crewId}
CREW_MEMBER_FIELDS = ("members", "joinProof", "multiplierReductions", "baselineMultipliers", "paidMembers")


def encode_email(email: str) -> str:
    """Must match UserRepository.encodeEmail in the app."""
    return email.lower().strip().replace(".", ",")


@app.post(
    "/account/delete",
    responses={
        401: {"description": "Missing or invalid Firebase authentication"},
        503: {"description": "Firebase server configuration is missing"},
    },
)
def delete_account(authorization: Annotated[str | None, Header()] = None) -> dict[str, str]:
    """Deletes the caller's data and Firebase Auth account. Payment records are retained."""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Firebase sign-in is required")
    root = get_database_root()
    try:
        firebase_user = auth.verify_id_token(authorization.removeprefix("Bearer "), check_revoked=True)
    except Exception as error:
        raise HTTPException(status_code=401, detail="Firebase token is invalid") from error
    uid = firebase_user["uid"]

    updates: dict[str, None] = {}
    crew_ids = (root.child("userCrews").child(uid).get(shallow=True) or {}).keys()
    for crew_id in crew_ids:
        crew = root.child("crews").child(crew_id).get()
        if not isinstance(crew, dict) or uid not in (crew.get("members") or {}):
            continue
        if not [member for member in crew["members"] if member != uid]:
            # Last member leaving: remove the crew entirely
            updates[f"crews/{crew_id}"] = None
            updates[f"groupChallengeProgress/{crew_id}"] = None
            if crew.get("inviteCode"):
                updates[f"inviteCodes/{crew['inviteCode']}"] = None
            continue
        for field in CREW_MEMBER_FIELDS:
            updates[f"crews/{crew_id}/{field}/{uid}"] = None
        challenge_ids = root.child("groupChallengeProgress").child(crew_id).get(shallow=True) or {}
        for challenge_id in challenge_ids:
            updates[f"groupChallengeProgress/{crew_id}/{challenge_id}/{uid}"] = None

    for node in USER_OWNED_NODES:
        updates[f"{node}/{uid}"] = None
    email = firebase_user.get("email")
    if email:
        email_key = encode_email(email)
        if root.child("emailIndex").child(email_key).get() == uid:
            updates[f"emailIndex/{email_key}"] = None

    root.update(updates)
    try:
        auth.delete_user(uid)
    except auth.UserNotFoundError:
        pass
    return {"status": "deleted"}


def support_email() -> str:
    return os.environ.get("SUPPORT_EMAIL", "").strip()


def contact_html() -> str:
    email = support_email()
    if not email:
        return "<em>Contact email not configured.</em>"
    safe = html.escape(email)
    return f'<a href="mailto:{safe}">{safe}</a>'


def render_page(title: str, body_html: str) -> HTMLResponse:
    return HTMLResponse(f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)} · ByteShare</title>
<style>
  body {{ background:#FDFBF0; color:#1A1A1A; font:16px/1.6 system-ui,-apple-system,sans-serif; margin:0; }}
  main {{ max-width:720px; margin:0 auto; padding:32px 16px 64px; }}
  h1 {{ font-size:2rem; margin-bottom:4px; }} h2 {{ margin-top:32px; }}
  .card {{ border:2px solid #1A1A1A; box-shadow:4px 4px 0 #1A1A1A; background:#fff; border-radius:16px; padding:16px 20px; }}
  a {{ color:#1A1A1A; }} .muted {{ color:#666; }}
</style></head><body><main>{body_html}</main></body></html>""")


@app.get("/delete-account", response_class=HTMLResponse)
def delete_account_page() -> HTMLResponse:
    return render_page("Delete your account", f"""
<h1>Delete your ByteShare account</h1>
<p class="muted">ByteShare · Account and data deletion</p>
<div class="card">
<h2 style="margin-top:0">In the app</h2>
<ol><li>Open ByteShare and go to the <strong>Me</strong> tab.</li>
<li>Scroll to <strong>Account</strong> and tap <strong>Delete Account</strong>.</li>
<li>Confirm. Your account is deleted immediately.</li></ol>
<h2>Without the app</h2>
<p>Email {contact_html()} from the email address on your ByteShare account with the subject
"Delete my account". We will delete it within 30 days and confirm by email.</p>
</div>
<h2>What is deleted</h2>
<p>Your profile (name, email, photo), screen-time totals, XP and streak, challenge progress,
nudges you received, and your membership in every crew. Crews with no other members are deleted.</p>
<h2>What is kept</h2>
<p>Records of payments you made (amount, crew, date) are kept as required for financial and tax
obligations. Nudges you sent to others stay in their inbox until the end of that day.
Card details are held by Stripe, not ByteShare.</p>
<p>Deleting your account does not cancel a ByteShare Pro subscription. Cancel it in
Google Play &rarr; Payments &amp; subscriptions.</p>
""")


@app.get("/privacy", response_class=HTMLResponse)
def privacy_policy_page() -> HTMLResponse:
    return render_page("Privacy Policy", f"""
<h1>Privacy Policy</h1>
<p class="muted">ByteShare · Last updated October 2026</p>
<p>ByteShare lets friends compare screen time and split shared bills based on it. This policy
explains what we collect, why, and who can see it.</p>

<h2>What we collect</h2>
<ul>
<li><strong>Account:</strong> your name, email address and profile photo from Google Sign-In, or an
anonymous ID if you continue as a guest.</li>
<li><strong>Screen time:</strong> with your permission (Usage Access), the app measures time spent in
apps on your device. We upload only daily and 7-day <em>totals per category</em> (social, streaming,
neutral, productivity). App names and activity inside apps stay on your device.</li>
<li><strong>Contacts:</strong> if you allow it, the app reads email addresses in your contacts and checks
each one against ByteShare accounts to find friends. Contacts are not stored.</li>
<li><strong>Crews and challenges:</strong> crews you create or join, bill amounts, invite codes,
challenge progress, XP, streaks and nudges you send or receive.</li>
<li><strong>Payments:</strong> when you pay your share, Stripe processes your payment. We store the amount,
crew and payment status; your card details are handled by Stripe.</li>
<li><strong>Ads and subscriptions:</strong> Google AdMob may collect your device's advertising ID and
device information to show ads. ByteShare Pro subscriptions are processed by Google Play and
managed through RevenueCat.</li>
</ul>

<h2>Who can see your data</h2>
<p>Friends who find you through their contacts, and members of crews you join, can see your name,
photo, screen-time category totals, rank, streak and payment status in those crews. We do not sell
your personal data.</p>

<h2>Service providers</h2>
<p>Google Firebase (sign-in and database), Render (our server), Stripe (payments),
Google AdMob (ads), Google Play and RevenueCat (subscriptions). Each processes data under its own
privacy policy.</p>

<h2>Retention and deletion</h2>
<p>We keep your data until you delete your account. Delete it anytime in the app under
<strong>Me &rarr; Account &rarr; Delete Account</strong>, or see <a href="/delete-account">how to delete
your account</a>. Payment records are kept as required by law.</p>

<h2>Your choices</h2>
<p>You can revoke Usage Access and Contacts permission in your device settings at any time.
ByteShare is not directed at children under 13.</p>

<h2>Contact</h2>
<p>Questions about this policy: {contact_html()}</p>
""")


handler = Mangum(app)


@https_fn.on_request()
def api(req: https_fn.Request) -> https_fn.Response:
    return handler(req)