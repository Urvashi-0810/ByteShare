package routes

import (
	"crypto/rand"
	"math"
	"strings"
	"time"

	"github.com/pocketbase/pocketbase/core"
)

const couponCodeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

// haversineMeters returns the great-circle distance in meters between two
// lat/lng points.
func haversineMeters(lat1, lng1, lat2, lng2 float64) float64 {
	const earthRadiusM = 6371000.0
	toRad := func(d float64) float64 { return d * math.Pi / 180.0 }

	dLat := toRad(lat2 - lat1)
	dLng := toRad(lng2 - lng1)
	a := math.Sin(dLat/2)*math.Sin(dLat/2) +
		math.Cos(toRad(lat1))*math.Cos(toRad(lat2))*
			math.Sin(dLng/2)*math.Sin(dLng/2)
	c := 2 * math.Atan2(math.Sqrt(a), math.Sqrt(1-a))
	return earthRadiusM * c
}

// randomCouponCode generates an unambiguous upper-case alphanumeric code.
func randomCouponCode(n int) string {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		for i := range b {
			b[i] = couponCodeAlphabet[i%len(couponCodeAlphabet)]
		}
		return string(b)
	}
	for i := range b {
		b[i] = couponCodeAlphabet[int(b[i])%len(couponCodeAlphabet)]
	}
	return string(b)
}

func nowISO() string {
	return time.Now().UTC().Format("2006-01-02 15:04:05.000Z")
}

// partnerDTO is a public view of a business partner, safe to embed in promoted
// challenge payloads.
type partnerDTO struct {
	ID        string  `json:"id"`
	Name      string  `json:"name"`
	LogoEmoji string  `json:"logoEmoji"`
	Address   string  `json:"address"`
	Lat       float64 `json:"lat"`
	Lng       float64 `json:"lng"`
}

func mapPartnerRecord(rec *core.Record) partnerDTO {
	logo := strings.TrimSpace(rec.GetString("logo_emoji"))
	if logo == "" {
		logo = "🏪"
	}
	return partnerDTO{
		ID:        rec.Id,
		Name:      rec.GetString("name"),
		LogoEmoji: logo,
		Address:   rec.GetString("address"),
		Lat:       rec.GetFloat("lat"),
		Lng:       rec.GetFloat("lng"),
	}
}

// couponPreviewDTO is a public preview of a coupon template (no code).
type couponPreviewDTO struct {
	ID             string  `json:"id"`
	Title          string  `json:"title"`
	DiscountText   string  `json:"discountText"`
	Terms          string  `json:"terms"`
	TotalAvailable int     `json:"totalAvailable"`
	IssuedCount    int     `json:"issuedCount"`
	Remaining      int     `json:"remaining"`
	ExpiresAt      string  `json:"expiresAt"`
}

func mapCouponPreviewRecord(rec *core.Record) couponPreviewDTO {
	total := rec.GetInt("total_available")
	issued := rec.GetInt("issued_count")
	remaining := total - issued
	if remaining < 0 {
		remaining = 0
	}
	return couponPreviewDTO{
		ID:             rec.Id,
		Title:          rec.GetString("title"),
		DiscountText:   rec.GetString("discount_text"),
		Terms:          rec.GetString("terms"),
		TotalAvailable: total,
		IssuedCount:    issued,
		Remaining:      remaining,
		ExpiresAt:      rec.GetString("expires_at"),
	}
}

// geoChallengeDTO is the public shape returned by the /geo/* endpoints.
type geoChallengeDTO struct {
	ID                string             `json:"id"`
	Title             string             `json:"title"`
	Description       string             `json:"description"`
	TargetLat         float64            `json:"targetLat"`
	TargetLng         float64            `json:"targetLng"`
	TargetLabel       string             `json:"targetLabel"`
	TargetRadiusM     int                `json:"targetRadiusM"`
	OriginRadiusM     int                `json:"originRadiusM"`
	MinElapsedSeconds int                `json:"minElapsedSeconds"`
	XPReward          int                `json:"xpReward"`
	Active            bool               `json:"active"`
	IsPromoted        bool               `json:"isPromoted"`
	Partner           *partnerDTO        `json:"partner,omitempty"`
	Coupons           []couponPreviewDTO `json:"coupons,omitempty"`
	DistanceM         *float64           `json:"distanceM,omitempty"`
	Created           string             `json:"created"`
}

func mapGeoChallengeRecord(app core.App, rec *core.Record, userLat, userLng *float64) geoChallengeDTO {
	dto := geoChallengeDTO{
		ID:                rec.Id,
		Title:             rec.GetString("title"),
		Description:       rec.GetString("description"),
		TargetLat:         rec.GetFloat("target_lat"),
		TargetLng:         rec.GetFloat("target_lng"),
		TargetLabel:       rec.GetString("target_label"),
		TargetRadiusM:     rec.GetInt("target_radius_m"),
		OriginRadiusM:     rec.GetInt("origin_radius_m"),
		MinElapsedSeconds: rec.GetInt("min_elapsed_seconds"),
		XPReward:          rec.GetInt("xp_reward"),
		Active:            rec.GetBool("active"),
		Created:           rec.GetString("created"),
	}
	if dto.TargetRadiusM <= 0 {
		dto.TargetRadiusM = 30
	}
	if dto.OriginRadiusM <= 0 {
		dto.OriginRadiusM = 50
	}

	if partnerID := rec.GetString("partner"); partnerID != "" {
		if partnerRec, err := app.FindRecordById("partners", partnerID); err == nil && partnerRec != nil {
			p := mapPartnerRecord(partnerRec)
			dto.Partner = &p
			dto.IsPromoted = true
		}
	}

	if coupons, err := app.FindRecordsByFilter(
		"coupons",
		"challenge = {:cid}",
		"created",
		50,
		0,
		map[string]any{"cid": rec.Id},
	); err == nil {
		for _, c := range coupons {
			dto.Coupons = append(dto.Coupons, mapCouponPreviewRecord(c))
		}
	}

	if userLat != nil && userLng != nil {
		d := haversineMeters(*userLat, *userLng, dto.TargetLat, dto.TargetLng)
		dto.DistanceM = &d
	}

	return dto
}

// geoAttemptDTO is the public shape for an in-progress or completed attempt.
type geoAttemptDTO struct {
	ID                string   `json:"id"`
	ChallengeID       string   `json:"challengeId"`
	UserID            string   `json:"userId"`
	Status            string   `json:"status"`
	OriginLat         float64  `json:"originLat"`
	OriginLng         float64  `json:"originLng"`
	LastLat           float64  `json:"lastLat"`
	LastLng           float64  `json:"lastLng"`
	Started           string   `json:"started"`
	ReachedTargetAt   string   `json:"reachedTargetAt,omitempty"`
	CompletedAt       string   `json:"completedAt,omitempty"`
	LastCheckin       string   `json:"lastCheckin,omitempty"`
	DistanceToTargetM *float64 `json:"distanceToTargetM,omitempty"`
	DistanceToOriginM *float64 `json:"distanceToOriginM,omitempty"`
}

func mapGeoAttemptRecord(rec *core.Record) geoAttemptDTO {
	return geoAttemptDTO{
		ID:              rec.Id,
		ChallengeID:     rec.GetString("challenge"),
		UserID:          rec.GetString("user"),
		Status:          rec.GetString("status"),
		OriginLat:       rec.GetFloat("origin_lat"),
		OriginLng:       rec.GetFloat("origin_lng"),
		LastLat:         rec.GetFloat("last_lat"),
		LastLng:         rec.GetFloat("last_lng"),
		Started:         rec.GetString("started"),
		ReachedTargetAt: rec.GetString("reached_target_at"),
		CompletedAt:     rec.GetString("completed_at"),
		LastCheckin:     rec.GetString("last_checkin"),
	}
}
