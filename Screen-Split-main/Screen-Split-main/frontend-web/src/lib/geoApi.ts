import { pb } from "@/lib/pocketbase";

export type GeoAttemptStatus =
  | "in_progress"
  | "reached_target"
  | "completed"
  | "failed";

export type CouponPreview = {
  id: string;
  title: string;
  discountText: string;
  terms: string;
  totalAvailable: number;
  issuedCount: number;
  remaining: number;
  expiresAt: string;
};

export type PartnerDTO = {
  id: string;
  name: string;
  logoEmoji: string;
  address: string;
  lat: number;
  lng: number;
};

export type GeoChallengeDTO = {
  id: string;
  title: string;
  description: string;
  targetLat: number;
  targetLng: number;
  targetLabel: string;
  targetRadiusM: number;
  originRadiusM: number;
  minElapsedSeconds: number;
  xpReward: number;
  active: boolean;
  isPromoted: boolean;
  partner?: PartnerDTO;
  coupons?: CouponPreview[];
  distanceM?: number;
  created: string;
};

export type GeoAttemptDTO = {
  id: string;
  challengeId: string;
  userId: string;
  status: GeoAttemptStatus;
  originLat: number;
  originLng: number;
  lastLat: number;
  lastLng: number;
  started: string;
  reachedTargetAt?: string;
  completedAt?: string;
  lastCheckin?: string;
};

export type CheckinResponse = {
  attempt: GeoAttemptDTO;
  distanceToTargetM: number;
  distanceToOriginM: number;
  targetRadiusM: number;
  originRadiusM: number;
  minElapsedSeconds: number;
  elapsedSeconds: number;
};

export type IssuedCouponDTO = {
  code: string;
  title: string;
  discountText: string;
  terms: string;
  expiresAt: string;
  partnerName?: string;
  partnerLogo?: string;
  createdAt: string;
};

export type CompleteResponse = {
  attempt: GeoAttemptDTO;
  xpAward: number;
  coupon?: IssuedCouponDTO;
};

export type MyCouponRow = {
  id: string;
  code: string;
  title: string;
  discountText: string;
  terms: string;
  expiresAt: string;
  redeemed: boolean;
  redeemedAt?: string;
  createdAt: string;
  challengeId: string;
  challengeName: string;
  partnerName?: string;
  partnerLogo?: string;
  expired: boolean;
};

export type StartAttemptResponse = {
  attempt: GeoAttemptDTO;
  challenge: GeoChallengeDTO;
  reused: boolean;
};

export async function listGeoChallenges(opts?: {
  lat?: number;
  lng?: number;
  radiusKm?: number;
}): Promise<GeoChallengeDTO[]> {
  const params = new URLSearchParams();
  if (opts?.lat !== undefined) params.set("lat", String(opts.lat));
  if (opts?.lng !== undefined) params.set("lng", String(opts.lng));
  if (opts?.radiusKm !== undefined) params.set("radius_km", String(opts.radiusKm));
  const qs = params.toString();
  const res = await pb.send<{ items: GeoChallengeDTO[] }>(
    `/geo/challenges${qs ? `?${qs}` : ""}`,
    { method: "GET" },
  );
  return res.items ?? [];
}

export async function getGeoChallenge(
  id: string,
  opts?: { lat?: number; lng?: number },
): Promise<GeoChallengeDTO> {
  const params = new URLSearchParams();
  if (opts?.lat !== undefined) params.set("lat", String(opts.lat));
  if (opts?.lng !== undefined) params.set("lng", String(opts.lng));
  const qs = params.toString();
  return pb.send<GeoChallengeDTO>(
    `/geo/challenges/${id}${qs ? `?${qs}` : ""}`,
    { method: "GET" },
  );
}

export async function startGeoAttempt(
  challengeId: string,
  body: { lat: number; lng: number },
): Promise<StartAttemptResponse> {
  return pb.send<StartAttemptResponse>(`/geo/challenges/${challengeId}/start`, {
    method: "POST",
    body,
  });
}

export async function checkinGeoAttempt(
  attemptId: string,
  body: { lat: number; lng: number },
): Promise<CheckinResponse> {
  return pb.send<CheckinResponse>(`/geo/attempts/${attemptId}/checkin`, {
    method: "POST",
    body,
  });
}

export async function completeGeoAttempt(
  attemptId: string,
): Promise<CompleteResponse> {
  return pb.send<CompleteResponse>(`/geo/attempts/${attemptId}/complete`, {
    method: "POST",
  });
}

export async function abandonGeoAttempt(attemptId: string): Promise<void> {
  await pb.send(`/geo/attempts/${attemptId}/abandon`, {
    method: "POST",
  });
}

export async function listMyAttempts(): Promise<GeoAttemptDTO[]> {
  const res = await pb.send<{ items: GeoAttemptDTO[] }>("/geo/attempts/mine", {
    method: "GET",
  });
  return res.items ?? [];
}

export async function listMyCoupons(): Promise<MyCouponRow[]> {
  const res = await pb.send<{ items: MyCouponRow[] }>("/coupons/mine", {
    method: "GET",
  });
  return res.items ?? [];
}

// --- Partner API ---

export type PartnerStats = {
  activeChallenges: number;
  totalCoupons: number;
  issuedCoupons: number;
  redeemedCoupons: number;
};

export type PartnerProfileResponse = {
  partner: PartnerDTO | null;
  challenges: GeoChallengeDTO[];
  stats: PartnerStats;
};

export type PartnerRedemption = {
  id: string;
  code: string;
  userId: string;
  userName: string;
  userEmail: string;
  challengeId: string;
  couponId: string;
  couponTitle: string;
  redeemed: boolean;
  redeemedAt?: string;
  createdAt: string;
};

export async function getPartnerMe(): Promise<PartnerProfileResponse> {
  return pb.send<PartnerProfileResponse>("/partners/me", { method: "GET" });
}

export async function upsertPartnerMe(body: {
  name: string;
  logoEmoji: string;
  address: string;
  lat: number;
  lng: number;
}): Promise<PartnerDTO> {
  return pb.send<PartnerDTO>("/partners/me", {
    method: "POST",
    body,
  });
}

export async function becomePartner(): Promise<{ ok: boolean; isPartner: boolean }> {
  return pb.send<{ ok: boolean; isPartner: boolean }>("/partners/become", {
    method: "POST",
  });
}

export async function createPartnerChallenge(body: {
  title: string;
  description?: string;
  targetLat: number;
  targetLng: number;
  targetLabel?: string;
  targetRadiusM?: number;
  originRadiusM?: number;
  minElapsedSeconds?: number;
  xpReward?: number;
  active?: boolean;
}): Promise<GeoChallengeDTO> {
  return pb.send<GeoChallengeDTO>("/partners/challenges", {
    method: "POST",
    body,
  });
}

export async function updatePartnerChallenge(
  id: string,
  body: Partial<{
    title: string;
    description: string;
    targetLat: number;
    targetLng: number;
    targetLabel: string;
    targetRadiusM: number;
    originRadiusM: number;
    minElapsedSeconds: number;
    xpReward: number;
    active: boolean;
  }>,
): Promise<GeoChallengeDTO> {
  return pb.send<GeoChallengeDTO>(`/partners/challenges/${id}`, {
    method: "PATCH",
    body,
  });
}

export async function deletePartnerChallenge(id: string): Promise<void> {
  await pb.send(`/partners/challenges/${id}`, { method: "DELETE" });
}

export async function createPartnerCoupon(body: {
  challengeId: string;
  title: string;
  discountText: string;
  terms?: string;
  totalAvailable: number;
  expiresAt?: string;
}): Promise<CouponPreview> {
  return pb.send<CouponPreview>("/partners/coupons", {
    method: "POST",
    body,
  });
}

export async function deletePartnerCoupon(id: string): Promise<void> {
  await pb.send(`/partners/coupons/${id}`, { method: "DELETE" });
}

export async function listPartnerRedemptions(): Promise<PartnerRedemption[]> {
  const res = await pb.send<{ items: PartnerRedemption[] }>(
    "/partners/redemptions",
    { method: "GET" },
  );
  return res.items ?? [];
}

export async function markPartnerRedemption(
  code: string,
): Promise<{ ok: boolean; code: string; redeemedAt?: string; alreadyRedeemed?: boolean }> {
  const res = await pb.send<{
    ok: boolean;
    code: string;
    redeemedAt?: string;
    alreadyRedeemed?: boolean;
  }>(`/partners/redemptions/${encodeURIComponent(code)}/mark-redeemed`, {
    method: "POST",
  });
  return res;
}
