package routes

import (
	"net/http"
	"strings"
	"time"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

type partnerProfileResponse struct {
	Partner    partnerDTO        `json:"partner"`
	Challenges []geoChallengeDTO `json:"challenges"`
	Stats      partnerStats      `json:"stats"`
}

type partnerStats struct {
	ActiveChallenges int `json:"activeChallenges"`
	TotalCoupons     int `json:"totalCoupons"`
	IssuedCoupons    int `json:"issuedCoupons"`
	RedeemedCoupons  int `json:"redeemedCoupons"`
}

type upsertPartnerBody struct {
	Name      string  `json:"name"`
	LogoEmoji string  `json:"logoEmoji"`
	Address   string  `json:"address"`
	Lat       float64 `json:"lat"`
	Lng       float64 `json:"lng"`
}

type upsertChallengeBody struct {
	Title             string   `json:"title"`
	Description       string   `json:"description"`
	TargetLat         float64  `json:"targetLat"`
	TargetLng         float64  `json:"targetLng"`
	TargetLabel       string   `json:"targetLabel"`
	TargetRadiusM     int      `json:"targetRadiusM"`
	OriginRadiusM     int      `json:"originRadiusM"`
	MinElapsedSeconds int      `json:"minElapsedSeconds"`
	XPReward          int      `json:"xpReward"`
	Active            *bool    `json:"active"`
}

type upsertCouponBody struct {
	ChallengeID    string `json:"challengeId"`
	Title          string `json:"title"`
	DiscountText   string `json:"discountText"`
	Terms          string `json:"terms"`
	TotalAvailable int    `json:"totalAvailable"`
	ExpiresAt      string `json:"expiresAt"`
}

type partnerRedemptionRow struct {
	ID          string `json:"id"`
	Code        string `json:"code"`
	UserID      string `json:"userId"`
	UserName    string `json:"userName"`
	UserEmail   string `json:"userEmail"`
	ChallengeID string `json:"challengeId"`
	CouponID    string `json:"couponId"`
	CouponTitle string `json:"couponTitle"`
	Redeemed    bool   `json:"redeemed"`
	RedeemedAt  string `json:"redeemedAt,omitempty"`
	CreatedAt   string `json:"createdAt"`
}

// RegisterPartnerRoutes registers routes only accessible by users marked
// is_partner = true.
func RegisterPartnerRoutes(se *core.ServeEvent) {
	se.Router.GET("/partners/me", getPartnerProfile).Bind(apis.RequireAuth("users"))
	se.Router.POST("/partners/me", upsertPartnerProfile).Bind(apis.RequireAuth("users"))
	se.Router.POST("/partners/become", becomePartner).Bind(apis.RequireAuth("users"))
	se.Router.POST("/partners/challenges", createPartnerChallenge).Bind(apis.RequireAuth("users"))
	se.Router.PATCH("/partners/challenges/{id}", updatePartnerChallenge).Bind(apis.RequireAuth("users"))
	se.Router.DELETE("/partners/challenges/{id}", deletePartnerChallenge).Bind(apis.RequireAuth("users"))
	se.Router.POST("/partners/coupons", createPartnerCoupon).Bind(apis.RequireAuth("users"))
	se.Router.DELETE("/partners/coupons/{id}", deletePartnerCoupon).Bind(apis.RequireAuth("users"))
	se.Router.GET("/partners/redemptions", listPartnerRedemptions).Bind(apis.RequireAuth("users"))
	se.Router.POST("/partners/redemptions/{code}/mark-redeemed", markRedemption).Bind(apis.RequireAuth("users"))
}

func requirePartner(re *core.RequestEvent) (*core.Record, *core.Record, error) {
	me := re.Auth
	if me == nil {
		return nil, nil, re.UnauthorizedError("missing auth", nil)
	}
	if !me.GetBool("is_partner") {
		return nil, nil, re.ForbiddenError("partner role required", nil)
	}
	recs, err := re.App.FindRecordsByFilter(
		"partners",
		"owner = {:id}",
		"",
		1,
		0,
		dbx.Params{"id": me.Id},
	)
	if err != nil {
		return me, nil, err
	}
	if len(recs) == 0 {
		return me, nil, nil
	}
	return me, recs[0], nil
}

func becomePartner(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}
	userRec, err := re.App.FindRecordById("users", me.Id)
	if err != nil || userRec == nil {
		return re.NotFoundError("user not found", nil)
	}
	userRec.Set("is_partner", true)
	if err := re.App.Save(userRec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, map[string]any{"ok": true, "isPartner": true})
}

func getPartnerProfile(re *core.RequestEvent) error {
	me, partnerRec, err := requirePartner(re)
	if err != nil {
		return err
	}

	if partnerRec == nil {
		return re.JSON(http.StatusOK, map[string]any{
			"partner":    nil,
			"challenges": []geoChallengeDTO{},
			"stats":      partnerStats{},
		})
	}

	challenges, err := re.App.FindRecordsByFilter(
		"geo_challenges",
		"partner = {:p}",
		"-created",
		200,
		0,
		dbx.Params{"p": partnerRec.Id},
	)
	if err != nil {
		return err
	}

	challengeDTOs := make([]geoChallengeDTO, 0, len(challenges))
	challengeIDs := make([]any, 0, len(challenges))
	activeCount := 0
	for _, c := range challenges {
		challengeDTOs = append(challengeDTOs, mapGeoChallengeRecord(re.App, c, nil, nil))
		challengeIDs = append(challengeIDs, c.Id)
		if c.GetBool("active") {
			activeCount++
		}
	}

	stats := partnerStats{ActiveChallenges: activeCount}

	if len(challengeIDs) > 0 {
		// {:0}, {:1} ... cannot be used directly with IN on dbx.Params; build filter.
		inFilter, params := buildInFilter("challenge", challengeIDs)
		coupons, err := re.App.FindRecordsByFilter(
			"coupons",
			inFilter,
			"",
			500,
			0,
			params,
		)
		if err == nil {
			stats.TotalCoupons = len(coupons)
			for _, c := range coupons {
				stats.IssuedCoupons += c.GetInt("issued_count")
			}
		}
	}

	redCount, _ := countPartnerRedemptions(re.App, partnerRec.Id, true)
	stats.RedeemedCoupons = redCount

	_ = me
	return re.JSON(http.StatusOK, partnerProfileResponse{
		Partner:    mapPartnerRecord(partnerRec),
		Challenges: challengeDTOs,
		Stats:      stats,
	})
}

func upsertPartnerProfile(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	var body upsertPartnerBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}
	name := strings.TrimSpace(body.Name)
	if name == "" {
		return re.BadRequestError("name is required", nil)
	}
	logo := strings.TrimSpace(body.LogoEmoji)
	if logo == "" {
		logo = "🏪"
	}

	// Auto-grant partner role if not already set (simple demo-friendly flow).
	userRec, err := re.App.FindRecordById("users", me.Id)
	if err != nil || userRec == nil {
		return re.NotFoundError("user not found", nil)
	}
	if !userRec.GetBool("is_partner") {
		userRec.Set("is_partner", true)
		if err := re.App.Save(userRec); err != nil {
			return err
		}
	}

	recs, err := re.App.FindRecordsByFilter(
		"partners",
		"owner = {:id}",
		"",
		1,
		0,
		dbx.Params{"id": me.Id},
	)
	if err != nil {
		return err
	}

	var rec *core.Record
	if len(recs) == 0 {
		col, err := re.App.FindCollectionByNameOrId("partners")
		if err != nil {
			return err
		}
		rec = core.NewRecord(col)
		rec.Set("owner", me.Id)
	} else {
		rec = recs[0]
	}

	rec.Set("name", name)
	rec.Set("logo_emoji", logo)
	rec.Set("address", strings.TrimSpace(body.Address))
	rec.Set("lat", body.Lat)
	rec.Set("lng", body.Lng)
	if err := re.App.Save(rec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, mapPartnerRecord(rec))
}

func createPartnerChallenge(re *core.RequestEvent) error {
	me, partnerRec, err := requirePartner(re)
	if err != nil {
		return err
	}
	if partnerRec == nil {
		return re.BadRequestError("create partner profile first", nil)
	}

	var body upsertChallengeBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}
	title := strings.TrimSpace(body.Title)
	if title == "" {
		return re.BadRequestError("title is required", nil)
	}

	col, err := re.App.FindCollectionByNameOrId("geo_challenges")
	if err != nil {
		return err
	}
	rec := core.NewRecord(col)
	rec.Set("title", title)
	rec.Set("description", body.Description)
	rec.Set("target_lat", body.TargetLat)
	rec.Set("target_lng", body.TargetLng)
	rec.Set("target_label", body.TargetLabel)
	if body.TargetRadiusM <= 0 {
		body.TargetRadiusM = 30
	}
	if body.OriginRadiusM <= 0 {
		body.OriginRadiusM = 50
	}
	rec.Set("target_radius_m", body.TargetRadiusM)
	rec.Set("origin_radius_m", body.OriginRadiusM)
	rec.Set("min_elapsed_seconds", body.MinElapsedSeconds)
	rec.Set("xp_reward", body.XPReward)
	active := true
	if body.Active != nil {
		active = *body.Active
	}
	rec.Set("active", active)
	rec.Set("partner", partnerRec.Id)
	rec.Set("created_by", me.Id)
	if err := re.App.Save(rec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, mapGeoChallengeRecord(re.App, rec, nil, nil))
}

func updatePartnerChallenge(re *core.RequestEvent) error {
	_, partnerRec, err := requirePartner(re)
	if err != nil {
		return err
	}
	if partnerRec == nil {
		return re.BadRequestError("create partner profile first", nil)
	}
	id := re.Request.PathValue("id")
	if id == "" {
		return re.NotFoundError("missing challenge id", nil)
	}
	rec, err := re.App.FindRecordById("geo_challenges", id)
	if err != nil || rec == nil {
		return re.NotFoundError("challenge not found", nil)
	}
	if rec.GetString("partner") != partnerRec.Id {
		return re.ForbiddenError("not your challenge", nil)
	}

	var body upsertChallengeBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}
	if t := strings.TrimSpace(body.Title); t != "" {
		rec.Set("title", t)
	}
	rec.Set("description", body.Description)
	if body.TargetLat != 0 {
		rec.Set("target_lat", body.TargetLat)
	}
	if body.TargetLng != 0 {
		rec.Set("target_lng", body.TargetLng)
	}
	rec.Set("target_label", body.TargetLabel)
	if body.TargetRadiusM > 0 {
		rec.Set("target_radius_m", body.TargetRadiusM)
	}
	if body.OriginRadiusM > 0 {
		rec.Set("origin_radius_m", body.OriginRadiusM)
	}
	if body.MinElapsedSeconds >= 0 {
		rec.Set("min_elapsed_seconds", body.MinElapsedSeconds)
	}
	if body.XPReward >= 0 {
		rec.Set("xp_reward", body.XPReward)
	}
	if body.Active != nil {
		rec.Set("active", *body.Active)
	}
	if err := re.App.Save(rec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, mapGeoChallengeRecord(re.App, rec, nil, nil))
}

func deletePartnerChallenge(re *core.RequestEvent) error {
	_, partnerRec, err := requirePartner(re)
	if err != nil {
		return err
	}
	if partnerRec == nil {
		return re.BadRequestError("create partner profile first", nil)
	}
	id := re.Request.PathValue("id")
	rec, err := re.App.FindRecordById("geo_challenges", id)
	if err != nil || rec == nil {
		return re.NotFoundError("challenge not found", nil)
	}
	if rec.GetString("partner") != partnerRec.Id {
		return re.ForbiddenError("not your challenge", nil)
	}
	if err := re.App.Delete(rec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, map[string]any{"ok": true})
}

func createPartnerCoupon(re *core.RequestEvent) error {
	_, partnerRec, err := requirePartner(re)
	if err != nil {
		return err
	}
	if partnerRec == nil {
		return re.BadRequestError("create partner profile first", nil)
	}
	var body upsertCouponBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}
	if strings.TrimSpace(body.ChallengeID) == "" {
		return re.BadRequestError("challengeId is required", nil)
	}
	if strings.TrimSpace(body.Title) == "" || strings.TrimSpace(body.DiscountText) == "" {
		return re.BadRequestError("title and discountText are required", nil)
	}
	challenge, err := re.App.FindRecordById("geo_challenges", body.ChallengeID)
	if err != nil || challenge == nil {
		return re.NotFoundError("challenge not found", nil)
	}
	if challenge.GetString("partner") != partnerRec.Id {
		return re.ForbiddenError("not your challenge", nil)
	}

	col, err := re.App.FindCollectionByNameOrId("coupons")
	if err != nil {
		return err
	}
	rec := core.NewRecord(col)
	rec.Set("challenge", challenge.Id)
	rec.Set("title", strings.TrimSpace(body.Title))
	rec.Set("discount_text", strings.TrimSpace(body.DiscountText))
	rec.Set("terms", body.Terms)
	if body.TotalAvailable < 0 {
		body.TotalAvailable = 0
	}
	rec.Set("total_available", body.TotalAvailable)
	rec.Set("issued_count", 0)
	if ex := strings.TrimSpace(body.ExpiresAt); ex != "" {
		rec.Set("expires_at", ex)
	}
	if err := re.App.Save(rec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, mapCouponPreviewRecord(rec))
}

func deletePartnerCoupon(re *core.RequestEvent) error {
	_, partnerRec, err := requirePartner(re)
	if err != nil {
		return err
	}
	if partnerRec == nil {
		return re.BadRequestError("create partner profile first", nil)
	}
	id := re.Request.PathValue("id")
	rec, err := re.App.FindRecordById("coupons", id)
	if err != nil || rec == nil {
		return re.NotFoundError("coupon not found", nil)
	}
	challenge, err := re.App.FindRecordById("geo_challenges", rec.GetString("challenge"))
	if err != nil || challenge == nil || challenge.GetString("partner") != partnerRec.Id {
		return re.ForbiddenError("not your coupon", nil)
	}
	if err := re.App.Delete(rec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, map[string]any{"ok": true})
}

// buildInFilter builds a PocketBase filter string with numbered placeholders
// for an IN-style match (e.g. "field = {:a0} || field = {:a1}").
func buildInFilter(field string, values []any) (string, dbx.Params) {
	if len(values) == 0 {
		return field + " = ''", dbx.Params{}
	}
	parts := make([]string, 0, len(values))
	params := dbx.Params{}
	for i, v := range values {
		key := "v" + itoa(i)
		parts = append(parts, field+" = {:"+key+"}")
		params[key] = v
	}
	return "(" + strings.Join(parts, " || ") + ")", params
}

func itoa(i int) string {
	if i == 0 {
		return "0"
	}
	neg := i < 0
	if neg {
		i = -i
	}
	var buf [20]byte
	n := len(buf)
	for i > 0 {
		n--
		buf[n] = byte('0' + i%10)
		i /= 10
	}
	if neg {
		n--
		buf[n] = '-'
	}
	return string(buf[n:])
}

// countPartnerRedemptions counts redemptions for coupons owned by a partner,
// filtered by the redeemed flag when onlyRedeemed is true.
func countPartnerRedemptions(app core.App, partnerID string, onlyRedeemed bool) (int, error) {
	coupons, err := app.FindRecordsByFilter(
		"coupons",
		"",
		"",
		5000,
		0,
		dbx.Params{},
	)
	if err != nil {
		return 0, err
	}
	// Filter by partner ownership via challenge.
	validCouponIDs := make(map[string]bool)
	for _, c := range coupons {
		challenge, err := app.FindRecordById("geo_challenges", c.GetString("challenge"))
		if err != nil || challenge == nil {
			continue
		}
		if challenge.GetString("partner") == partnerID {
			validCouponIDs[c.Id] = true
		}
	}
	if len(validCouponIDs) == 0 {
		return 0, nil
	}
	ids := make([]any, 0, len(validCouponIDs))
	for id := range validCouponIDs {
		ids = append(ids, id)
	}
	inFilter, params := buildInFilter("coupon", ids)
	filter := inFilter
	if onlyRedeemed {
		filter = "(" + filter + ") && redeemed = true"
	}
	recs, err := app.FindRecordsByFilter("coupon_redemptions", filter, "", 10000, 0, params)
	if err != nil {
		return 0, err
	}
	return len(recs), nil
}

func listPartnerRedemptions(re *core.RequestEvent) error {
	_, partnerRec, err := requirePartner(re)
	if err != nil {
		return err
	}
	if partnerRec == nil {
		return re.BadRequestError("create partner profile first", nil)
	}

	// Get partner's coupon IDs via their challenges.
	challenges, err := re.App.FindRecordsByFilter(
		"geo_challenges",
		"partner = {:p}",
		"",
		500,
		0,
		dbx.Params{"p": partnerRec.Id},
	)
	if err != nil {
		return err
	}
	if len(challenges) == 0 {
		return re.JSON(http.StatusOK, map[string]any{"items": []partnerRedemptionRow{}})
	}
	challengeIDs := make([]any, 0, len(challenges))
	for _, c := range challenges {
		challengeIDs = append(challengeIDs, c.Id)
	}
	couponFilter, couponParams := buildInFilter("challenge", challengeIDs)
	coupons, err := re.App.FindRecordsByFilter("coupons", couponFilter, "", 1000, 0, couponParams)
	if err != nil {
		return err
	}
	if len(coupons) == 0 {
		return re.JSON(http.StatusOK, map[string]any{"items": []partnerRedemptionRow{}})
	}
	couponIDs := make([]any, 0, len(coupons))
	couponByID := make(map[string]*core.Record, len(coupons))
	for _, c := range coupons {
		couponIDs = append(couponIDs, c.Id)
		couponByID[c.Id] = c
	}
	redempFilter, redempParams := buildInFilter("coupon", couponIDs)
	redemptions, err := re.App.FindRecordsByFilter("coupon_redemptions", redempFilter, "-created", 500, 0, redempParams)
	if err != nil {
		return err
	}

	out := make([]partnerRedemptionRow, 0, len(redemptions))
	for _, r := range redemptions {
		coupon := couponByID[r.GetString("coupon")]
		if coupon == nil {
			continue
		}
		row := partnerRedemptionRow{
			ID:          r.Id,
			Code:        r.GetString("code"),
			UserID:      r.GetString("user"),
			ChallengeID: coupon.GetString("challenge"),
			CouponID:    coupon.Id,
			CouponTitle: coupon.GetString("title"),
			Redeemed:    r.GetBool("redeemed"),
			RedeemedAt:  r.GetString("redeemed_at"),
			CreatedAt:   r.GetString("created"),
		}
		if userRec, uerr := re.App.FindRecordById("users", row.UserID); uerr == nil && userRec != nil {
			row.UserName = userRec.GetString("name")
			row.UserEmail = userRec.GetString("email")
		}
		out = append(out, row)
	}

	return re.JSON(http.StatusOK, map[string]any{"items": out})
}

func markRedemption(re *core.RequestEvent) error {
	_, partnerRec, err := requirePartner(re)
	if err != nil {
		return err
	}
	if partnerRec == nil {
		return re.BadRequestError("create partner profile first", nil)
	}
	code := strings.TrimSpace(strings.ToUpper(re.Request.PathValue("code")))
	if code == "" {
		return re.BadRequestError("code is required", nil)
	}
	recs, err := re.App.FindRecordsByFilter(
		"coupon_redemptions",
		"code = {:c}",
		"",
		1,
		0,
		dbx.Params{"c": code},
	)
	if err != nil {
		return err
	}
	if len(recs) == 0 {
		return re.NotFoundError("no redemption with that code", nil)
	}
	rec := recs[0]
	coupon, err := re.App.FindRecordById("coupons", rec.GetString("coupon"))
	if err != nil || coupon == nil {
		return re.NotFoundError("coupon not found", nil)
	}
	challenge, err := re.App.FindRecordById("geo_challenges", coupon.GetString("challenge"))
	if err != nil || challenge == nil || challenge.GetString("partner") != partnerRec.Id {
		return re.ForbiddenError("not your coupon", nil)
	}
	if rec.GetBool("redeemed") {
		return re.JSON(http.StatusOK, map[string]any{
			"ok":        true,
			"alreadyRedeemed": true,
			"code":      code,
			"redeemedAt": rec.GetString("redeemed_at"),
		})
	}
	rec.Set("redeemed", true)
	rec.Set("redeemed_at", time.Now().UTC().Format("2006-01-02 15:04:05.000Z"))
	if err := re.App.Save(rec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, map[string]any{
		"ok":         true,
		"code":       code,
		"redeemedAt": rec.GetString("redeemed_at"),
	})
}
