package routes

import (
	"net/http"
	"strings"
	"time"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

type myCouponRow struct {
	ID            string `json:"id"`
	Code          string `json:"code"`
	Title         string `json:"title"`
	DiscountText  string `json:"discountText"`
	Terms         string `json:"terms"`
	ExpiresAt     string `json:"expiresAt"`
	Redeemed      bool   `json:"redeemed"`
	RedeemedAt    string `json:"redeemedAt,omitempty"`
	CreatedAt     string `json:"createdAt"`
	ChallengeID   string `json:"challengeId"`
	ChallengeName string `json:"challengeName"`
	PartnerName   string `json:"partnerName,omitempty"`
	PartnerLogo   string `json:"partnerLogo,omitempty"`
	Expired       bool   `json:"expired"`
}

type myCouponsResponse struct {
	Items []myCouponRow `json:"items"`
}

// RegisterCouponRoutes registers user-facing coupon routes.
func RegisterCouponRoutes(se *core.ServeEvent) {
	se.Router.GET("/coupons/mine", listMyCoupons).Bind(apis.RequireAuth("users"))
}

func listMyCoupons(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	recs, err := re.App.FindRecordsByFilter(
		"coupon_redemptions",
		"user = {:id}",
		"-created",
		200,
		0,
		dbx.Params{"id": me.Id},
	)
	if err != nil {
		return err
	}

	now := time.Now().UTC()
	out := make([]myCouponRow, 0, len(recs))
	for _, r := range recs {
		coupon, err := re.App.FindRecordById("coupons", r.GetString("coupon"))
		if err != nil || coupon == nil {
			continue
		}
		challenge, err := re.App.FindRecordById("geo_challenges", coupon.GetString("challenge"))
		if err != nil || challenge == nil {
			continue
		}
		row := myCouponRow{
			ID:            r.Id,
			Code:          r.GetString("code"),
			Title:         coupon.GetString("title"),
			DiscountText:  coupon.GetString("discount_text"),
			Terms:         coupon.GetString("terms"),
			ExpiresAt:     coupon.GetString("expires_at"),
			Redeemed:      r.GetBool("redeemed"),
			RedeemedAt:    r.GetString("redeemed_at"),
			CreatedAt:     r.GetString("created"),
			ChallengeID:   challenge.Id,
			ChallengeName: challenge.GetString("title"),
		}
		if partnerID := challenge.GetString("partner"); partnerID != "" {
			if partnerRec, err := re.App.FindRecordById("partners", partnerID); err == nil && partnerRec != nil {
				row.PartnerName = partnerRec.GetString("name")
				logo := strings.TrimSpace(partnerRec.GetString("logo_emoji"))
				if logo == "" {
					logo = "🏪"
				}
				row.PartnerLogo = logo
			}
		}
		if row.ExpiresAt != "" {
			for _, layout := range []string{"2006-01-02 15:04:05.000Z", "2006-01-02 15:04:05Z", time.RFC3339} {
				if t, perr := time.Parse(layout, row.ExpiresAt); perr == nil {
					if t.Before(now) {
						row.Expired = true
					}
					break
				}
			}
		}
		out = append(out, row)
	}

	return re.JSON(http.StatusOK, myCouponsResponse{Items: out})
}
