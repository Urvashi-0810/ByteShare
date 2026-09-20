package routes

import (
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

type geoChallengesListResponse struct {
	Items []geoChallengeDTO `json:"items"`
}

type startAttemptBody struct {
	Lat float64 `json:"lat"`
	Lng float64 `json:"lng"`
}

type checkinBody struct {
	Lat float64 `json:"lat"`
	Lng float64 `json:"lng"`
}

type checkinResponse struct {
	Attempt           geoAttemptDTO `json:"attempt"`
	DistanceToTargetM float64       `json:"distanceToTargetM"`
	DistanceToOriginM float64       `json:"distanceToOriginM"`
	TargetRadiusM     int           `json:"targetRadiusM"`
	OriginRadiusM     int           `json:"originRadiusM"`
	MinElapsedSeconds int           `json:"minElapsedSeconds"`
	ElapsedSeconds    int           `json:"elapsedSeconds"`
}

type issuedCouponDTO struct {
	Code         string `json:"code"`
	Title        string `json:"title"`
	DiscountText string `json:"discountText"`
	Terms        string `json:"terms"`
	ExpiresAt    string `json:"expiresAt"`
	PartnerName  string `json:"partnerName,omitempty"`
	PartnerLogo  string `json:"partnerLogo,omitempty"`
	CreatedAt    string `json:"createdAt"`
}

type completeResponse struct {
	Attempt geoAttemptDTO    `json:"attempt"`
	XPAward int              `json:"xpAward"`
	Coupon  *issuedCouponDTO `json:"coupon,omitempty"`
}

// RegisterGeoRoutes registers geo-challenge endpoints for end users.
func RegisterGeoRoutes(se *core.ServeEvent) {
	se.Router.GET("/geo/challenges", listGeoChallenges).Bind(apis.RequireAuth("users"))
	se.Router.GET("/geo/challenges/{id}", getGeoChallenge).Bind(apis.RequireAuth("users"))
	se.Router.GET("/geo/attempts/mine", listMyAttempts).Bind(apis.RequireAuth("users"))
	se.Router.POST("/geo/challenges/{id}/start", startAttempt).Bind(apis.RequireAuth("users"))
	se.Router.POST("/geo/attempts/{id}/checkin", checkinAttempt).Bind(apis.RequireAuth("users"))
	se.Router.POST("/geo/attempts/{id}/complete", completeAttempt).Bind(apis.RequireAuth("users"))
	se.Router.POST("/geo/attempts/{id}/abandon", abandonAttempt).Bind(apis.RequireAuth("users"))
}

func parseFloatQuery(re *core.RequestEvent, key string) *float64 {
	raw := strings.TrimSpace(re.Request.URL.Query().Get(key))
	if raw == "" {
		return nil
	}
	v, err := strconv.ParseFloat(raw, 64)
	if err != nil {
		return nil
	}
	return &v
}

func listGeoChallenges(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	lat := parseFloatQuery(re, "lat")
	lng := parseFloatQuery(re, "lng")
	radiusKmPtr := parseFloatQuery(re, "radius_km")

	recs, err := re.App.FindRecordsByFilter(
		"geo_challenges",
		"active = true",
		"-created",
		500,
		0,
		dbx.Params{},
	)
	if err != nil {
		return err
	}

	out := make([]geoChallengeDTO, 0, len(recs))
	for _, r := range recs {
		dto := mapGeoChallengeRecord(re.App, r, lat, lng)
		if radiusKmPtr != nil && dto.DistanceM != nil && *dto.DistanceM > (*radiusKmPtr*1000.0) {
			continue
		}
		out = append(out, dto)
	}

	return re.JSON(http.StatusOK, geoChallengesListResponse{Items: out})
}

func getGeoChallenge(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}
	id := re.Request.PathValue("id")
	if id == "" {
		return re.NotFoundError("missing challenge id", nil)
	}
	rec, err := re.App.FindRecordById("geo_challenges", id)
	if err != nil || rec == nil {
		return re.NotFoundError("challenge not found", nil)
	}
	lat := parseFloatQuery(re, "lat")
	lng := parseFloatQuery(re, "lng")
	dto := mapGeoChallengeRecord(re.App, rec, lat, lng)
	return re.JSON(http.StatusOK, dto)
}

func listMyAttempts(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}
	recs, err := re.App.FindRecordsByFilter(
		"geo_attempts",
		"user = {:id}",
		"-created",
		200,
		0,
		dbx.Params{"id": me.Id},
	)
	if err != nil {
		return err
	}
	out := make([]geoAttemptDTO, 0, len(recs))
	for _, r := range recs {
		out = append(out, mapGeoAttemptRecord(r))
	}
	return re.JSON(http.StatusOK, map[string]any{"items": out})
}

func findActiveAttempt(app core.App, userID, challengeID string) (*core.Record, error) {
	recs, err := app.FindRecordsByFilter(
		"geo_attempts",
		"user = {:u} && challenge = {:c} && (status = 'in_progress' || status = 'reached_target')",
		"-created",
		1,
		0,
		dbx.Params{"u": userID, "c": challengeID},
	)
	if err != nil {
		return nil, err
	}
	if len(recs) == 0 {
		return nil, nil
	}
	return recs[0], nil
}

func startAttempt(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}
	challengeID := re.Request.PathValue("id")
	if challengeID == "" {
		return re.NotFoundError("missing challenge id", nil)
	}
	var body startAttemptBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}

	challenge, err := re.App.FindRecordById("geo_challenges", challengeID)
	if err != nil || challenge == nil {
		return re.NotFoundError("challenge not found", nil)
	}
	if !challenge.GetBool("active") {
		return re.BadRequestError("challenge is not active", nil)
	}

	// Reuse any existing unfinished attempt instead of creating duplicates.
	if existing, err := findActiveAttempt(re.App, me.Id, challengeID); err != nil {
		return err
	} else if existing != nil {
		return re.JSON(http.StatusOK, map[string]any{
			"attempt":   mapGeoAttemptRecord(existing),
			"challenge": mapGeoChallengeRecord(re.App, challenge, nil, nil),
			"reused":    true,
		})
	}

	col, err := re.App.FindCollectionByNameOrId("geo_attempts")
	if err != nil {
		return err
	}

	rec := core.NewRecord(col)
	rec.Set("user", me.Id)
	rec.Set("challenge", challengeID)
	rec.Set("status", "in_progress")
	rec.Set("origin_lat", body.Lat)
	rec.Set("origin_lng", body.Lng)
	rec.Set("last_lat", body.Lat)
	rec.Set("last_lng", body.Lng)
	now := nowISO()
	rec.Set("started", now)
	rec.Set("last_checkin", now)
	if err := re.App.Save(rec); err != nil {
		return err
	}

	return re.JSON(http.StatusOK, map[string]any{
		"attempt":   mapGeoAttemptRecord(rec),
		"challenge": mapGeoChallengeRecord(re.App, challenge, nil, nil),
		"reused":    false,
	})
}

func loadMyAttempt(re *core.RequestEvent) (*core.Record, *core.Record, error) {
	me := re.Auth
	if me == nil {
		return nil, nil, re.UnauthorizedError("missing auth", nil)
	}
	id := re.Request.PathValue("id")
	if id == "" {
		return nil, nil, re.NotFoundError("missing attempt id", nil)
	}
	attempt, err := re.App.FindRecordById("geo_attempts", id)
	if err != nil || attempt == nil {
		return nil, nil, re.NotFoundError("attempt not found", nil)
	}
	if attempt.GetString("user") != me.Id {
		return nil, nil, re.ForbiddenError("not your attempt", nil)
	}
	challenge, err := re.App.FindRecordById("geo_challenges", attempt.GetString("challenge"))
	if err != nil || challenge == nil {
		return attempt, nil, re.NotFoundError("challenge not found", nil)
	}
	return attempt, challenge, nil
}

func checkinAttempt(re *core.RequestEvent) error {
	attempt, challenge, err := loadMyAttempt(re)
	if err != nil {
		return err
	}

	var body checkinBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}

	status := attempt.GetString("status")
	if status == "completed" || status == "failed" {
		dto := mapGeoAttemptRecord(attempt)
		return re.JSON(http.StatusOK, checkinResponse{
			Attempt: dto,
		})
	}

	targetLat := challenge.GetFloat("target_lat")
	targetLng := challenge.GetFloat("target_lng")
	targetRadius := challenge.GetInt("target_radius_m")
	if targetRadius <= 0 {
		targetRadius = 30
	}
	originRadius := challenge.GetInt("origin_radius_m")
	if originRadius <= 0 {
		originRadius = 50
	}
	minElapsed := challenge.GetInt("min_elapsed_seconds")

	originLat := attempt.GetFloat("origin_lat")
	originLng := attempt.GetFloat("origin_lng")

	distToTarget := haversineMeters(body.Lat, body.Lng, targetLat, targetLng)
	distToOrigin := haversineMeters(body.Lat, body.Lng, originLat, originLng)

	attempt.Set("last_lat", body.Lat)
	attempt.Set("last_lng", body.Lng)
	attempt.Set("last_checkin", nowISO())

	elapsed := 0
	if startedStr := attempt.GetString("started"); startedStr != "" {
		for _, layout := range []string{"2006-01-02 15:04:05.000Z", "2006-01-02 15:04:05Z", time.RFC3339} {
			if t, perr := time.Parse(layout, startedStr); perr == nil {
				elapsed = int(time.Since(t).Seconds())
				break
			}
		}
	}

	if status == "in_progress" && distToTarget <= float64(targetRadius) {
		attempt.Set("status", "reached_target")
		attempt.Set("reached_target_at", nowISO())
		status = "reached_target"
	}
	if status == "reached_target" && distToOrigin <= float64(originRadius) && elapsed >= minElapsed {
		attempt.Set("status", "completed")
		attempt.Set("completed_at", nowISO())
		status = "completed"
	}

	if err := re.App.Save(attempt); err != nil {
		return err
	}

	return re.JSON(http.StatusOK, checkinResponse{
		Attempt:           mapGeoAttemptRecord(attempt),
		DistanceToTargetM: distToTarget,
		DistanceToOriginM: distToOrigin,
		TargetRadiusM:     targetRadius,
		OriginRadiusM:     originRadius,
		MinElapsedSeconds: minElapsed,
		ElapsedSeconds:    elapsed,
	})
}

// pickIssuableCoupon finds the first coupon template for the challenge that has
// remaining inventory and is not expired.
func pickIssuableCoupon(app core.App, challengeID string) (*core.Record, error) {
	recs, err := app.FindRecordsByFilter(
		"coupons",
		"challenge = {:c}",
		"created",
		50,
		0,
		dbx.Params{"c": challengeID},
	)
	if err != nil {
		return nil, err
	}
	now := time.Now().UTC()
	for _, r := range recs {
		total := r.GetInt("total_available")
		issued := r.GetInt("issued_count")
		if total > 0 && issued >= total {
			continue
		}
		if expires := r.GetString("expires_at"); expires != "" {
			for _, layout := range []string{"2006-01-02 15:04:05.000Z", "2006-01-02 15:04:05Z", time.RFC3339} {
				if t, perr := time.Parse(layout, expires); perr == nil {
					if t.Before(now) {
						goto skip
					}
					break
				}
			}
		}
		return r, nil
	skip:
	}
	return nil, nil
}

// issueCouponForAttempt atomically increments issued_count and creates a unique
// redemption code for the current user.
func issueCouponForAttempt(app core.App, userID, attemptID, challengeID string) (*issuedCouponDTO, error) {
	coupon, err := pickIssuableCoupon(app, challengeID)
	if err != nil || coupon == nil {
		return nil, err
	}

	coupon.Set("issued_count", coupon.GetInt("issued_count")+1)
	if err := app.Save(coupon); err != nil {
		return nil, err
	}

	redempCol, err := app.FindCollectionByNameOrId("coupon_redemptions")
	if err != nil {
		return nil, err
	}

	var created *core.Record
	for attempt := 0; attempt < 10; attempt++ {
		rec := core.NewRecord(redempCol)
		rec.Set("user", userID)
		rec.Set("coupon", coupon.Id)
		rec.Set("attempt", attemptID)
		rec.Set("code", randomCouponCode(8))
		rec.Set("redeemed", false)
		if err := app.Save(rec); err != nil {
			if strings.Contains(strings.ToLower(err.Error()), "unique") {
				continue
			}
			return nil, err
		}
		created = rec
		break
	}
	if created == nil {
		return nil, nil
	}

	dto := &issuedCouponDTO{
		Code:         created.GetString("code"),
		Title:        coupon.GetString("title"),
		DiscountText: coupon.GetString("discount_text"),
		Terms:        coupon.GetString("terms"),
		ExpiresAt:    coupon.GetString("expires_at"),
		CreatedAt:    created.GetString("created"),
	}

	if challenge, err := app.FindRecordById("geo_challenges", challengeID); err == nil && challenge != nil {
		if partnerID := challenge.GetString("partner"); partnerID != "" {
			if partnerRec, err := app.FindRecordById("partners", partnerID); err == nil && partnerRec != nil {
				dto.PartnerName = partnerRec.GetString("name")
				logo := strings.TrimSpace(partnerRec.GetString("logo_emoji"))
				if logo == "" {
					logo = "🏪"
				}
				dto.PartnerLogo = logo
			}
		}
	}

	return dto, nil
}

func completeAttempt(re *core.RequestEvent) error {
	attempt, challenge, err := loadMyAttempt(re)
	if err != nil {
		return err
	}
	me := re.Auth

	status := attempt.GetString("status")
	if status != "completed" {
		return re.BadRequestError("attempt is not yet completed; keep checking in", nil)
	}

	xp := challenge.GetInt("xp_reward")

	// Award XP once on first completion finalization. We use a lightweight guard:
	// if attempt has already issued a coupon (redemption exists), skip.
	existing, _ := re.App.FindRecordsByFilter(
		"coupon_redemptions",
		"attempt = {:a}",
		"-created",
		1,
		0,
		dbx.Params{"a": attempt.Id},
	)

	resp := completeResponse{
		Attempt: mapGeoAttemptRecord(attempt),
		XPAward: xp,
	}

	if len(existing) > 0 {
		rec := existing[0]
		coupon, _ := re.App.FindRecordById("coupons", rec.GetString("coupon"))
		if coupon != nil {
			dto := &issuedCouponDTO{
				Code:         rec.GetString("code"),
				Title:        coupon.GetString("title"),
				DiscountText: coupon.GetString("discount_text"),
				Terms:        coupon.GetString("terms"),
				ExpiresAt:    coupon.GetString("expires_at"),
				CreatedAt:    rec.GetString("created"),
			}
			if partnerID := challenge.GetString("partner"); partnerID != "" {
				if partnerRec, err := re.App.FindRecordById("partners", partnerID); err == nil && partnerRec != nil {
					dto.PartnerName = partnerRec.GetString("name")
					logo := strings.TrimSpace(partnerRec.GetString("logo_emoji"))
					if logo == "" {
						logo = "🏪"
					}
					dto.PartnerLogo = logo
				}
			}
			resp.Coupon = dto
		}
		return re.JSON(http.StatusOK, resp)
	}

	// Bump user streak by 1 on first completion.
	if userRec, err := re.App.FindRecordById("users", me.Id); err == nil && userRec != nil {
		userRec.Set("streak", userRec.GetInt("streak")+1)
		_ = re.App.Save(userRec)
	}

	// Promoted: issue coupon.
	if challenge.GetString("partner") != "" {
		coupon, err := issueCouponForAttempt(re.App, me.Id, attempt.Id, challenge.Id)
		if err != nil {
			return err
		}
		resp.Coupon = coupon
	}

	return re.JSON(http.StatusOK, resp)
}

func abandonAttempt(re *core.RequestEvent) error {
	attempt, _, err := loadMyAttempt(re)
	if err != nil {
		return err
	}
	status := attempt.GetString("status")
	if status == "completed" {
		return re.BadRequestError("cannot abandon a completed attempt", nil)
	}
	attempt.Set("status", "failed")
	if err := re.App.Save(attempt); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, map[string]any{"ok": true, "attempt": mapGeoAttemptRecord(attempt)})
}
