package migrations

import (
	"database/sql"
	"errors"

	"github.com/pocketbase/pocketbase/core"
	m "github.com/pocketbase/pocketbase/migrations"
	"github.com/pocketbase/pocketbase/tools/types"
)

func init() {
	m.Register(func(app core.App) error {
		users, err := app.FindCollectionByNameOrId("users")
		if err != nil {
			return err
		}

		if users.Fields.GetByName("is_partner") == nil {
			users.Fields.Add(&core.BoolField{
				Name: "is_partner",
			})
			if err := app.Save(users); err != nil {
				return err
			}
		}

		// partners collection
		var partnersCol *core.Record // used as *core.Collection below via re-fetch
		_ = partnersCol
		partnersCollection, perr := app.FindCollectionByNameOrId("partners")
		if perr != nil && !errors.Is(perr, sql.ErrNoRows) {
			return perr
		}
		if partnersCollection == nil {
			partners := core.NewBaseCollection("partners")

			// All writes via /partners/me custom route. Public list/view so user app can show partner info on promoted challenges.
			partners.ListRule = types.Pointer("")
			partners.ViewRule = types.Pointer("")
			partners.CreateRule = nil
			partners.UpdateRule = nil
			partners.DeleteRule = nil

			partners.Indexes = append(partners.Indexes,
				"CREATE UNIQUE INDEX idx_partners_owner ON partners (owner)",
			)

			partners.Fields.Add(&core.RelationField{
				Name:          "owner",
				Required:      true,
				CollectionId:  users.Id,
				MaxSelect:     1,
				CascadeDelete: true,
			})
			partners.Fields.Add(&core.TextField{
				Name:     "name",
				Required: true,
				Max:      200,
			})
			partners.Fields.Add(&core.TextField{
				Name: "logo_emoji",
				Max:  16,
			})
			partners.Fields.Add(&core.TextField{
				Name: "address",
				Max:  500,
			})
			partners.Fields.Add(&core.NumberField{
				Name: "lat",
			})
			partners.Fields.Add(&core.NumberField{
				Name: "lng",
			})
			partners.Fields.Add(&core.AutodateField{
				Name:     "created",
				OnCreate: true,
			})
			partners.Fields.Add(&core.AutodateField{
				Name:     "updated",
				OnCreate: true,
				OnUpdate: true,
			})
			if err := app.Save(partners); err != nil {
				return err
			}
			partnersCollection = partners
		}

		// geo_challenges collection
		geoChallengesCol, gerr := app.FindCollectionByNameOrId("geo_challenges")
		if gerr != nil && !errors.Is(gerr, sql.ErrNoRows) {
			return gerr
		}
		if geoChallengesCol == nil {
			gc := core.NewBaseCollection("geo_challenges")

			// Public read for active challenges. Writes via /partners/challenges.
			gc.ListRule = types.Pointer("active = true")
			gc.ViewRule = types.Pointer("active = true || created_by = @request.auth.id")
			gc.CreateRule = nil
			gc.UpdateRule = nil
			gc.DeleteRule = nil

			gc.Fields.Add(&core.TextField{
				Name:     "title",
				Required: true,
				Max:      200,
			})
			gc.Fields.Add(&core.TextField{
				Name: "description",
				Max:  2000,
			})
			gc.Fields.Add(&core.NumberField{
				Name:     "target_lat",
				Required: true,
			})
			gc.Fields.Add(&core.NumberField{
				Name:     "target_lng",
				Required: true,
			})
			gc.Fields.Add(&core.TextField{
				Name: "target_label",
				Max:  200,
			})
			gc.Fields.Add(&core.NumberField{
				Name:    "target_radius_m",
				OnlyInt: true,
				Min:     types.Pointer(5.0),
			})
			gc.Fields.Add(&core.NumberField{
				Name:    "origin_radius_m",
				OnlyInt: true,
				Min:     types.Pointer(5.0),
			})
			gc.Fields.Add(&core.NumberField{
				Name:    "min_elapsed_seconds",
				OnlyInt: true,
				Min:     types.Pointer(0.0),
			})
			gc.Fields.Add(&core.NumberField{
				Name:    "xp_reward",
				OnlyInt: true,
				Min:     types.Pointer(0.0),
			})
			gc.Fields.Add(&core.BoolField{
				Name: "active",
			})
			gc.Fields.Add(&core.RelationField{
				Name:          "partner",
				Required:      false,
				CollectionId:  partnersCollection.Id,
				MaxSelect:     1,
				CascadeDelete: true,
			})
			gc.Fields.Add(&core.RelationField{
				Name:          "created_by",
				Required:      true,
				CollectionId:  users.Id,
				MaxSelect:     1,
				CascadeDelete: false,
			})
			gc.Fields.Add(&core.AutodateField{
				Name:     "created",
				OnCreate: true,
			})
			gc.Fields.Add(&core.AutodateField{
				Name:     "updated",
				OnCreate: true,
				OnUpdate: true,
			})
			if err := app.Save(gc); err != nil {
				return err
			}
			geoChallengesCol = gc
		}

		// geo_attempts collection
		geoAttemptsCol, aerr := app.FindCollectionByNameOrId("geo_attempts")
		if aerr != nil && !errors.Is(aerr, sql.ErrNoRows) {
			return aerr
		}
		if geoAttemptsCol == nil {
			ga := core.NewBaseCollection("geo_attempts")

			ownerRule := "user = @request.auth.id"
			ga.ListRule = types.Pointer(ownerRule)
			ga.ViewRule = types.Pointer(ownerRule)
			ga.CreateRule = nil
			ga.UpdateRule = nil
			ga.DeleteRule = nil

			ga.Fields.Add(&core.RelationField{
				Name:          "user",
				Required:      true,
				CollectionId:  users.Id,
				MaxSelect:     1,
				CascadeDelete: true,
			})
			ga.Fields.Add(&core.RelationField{
				Name:          "challenge",
				Required:      true,
				CollectionId:  geoChallengesCol.Id,
				MaxSelect:     1,
				CascadeDelete: true,
			})
			ga.Fields.Add(&core.SelectField{
				Name:      "status",
				Required:  true,
				MaxSelect: 1,
				Values:    []string{"in_progress", "reached_target", "completed", "failed"},
			})
			ga.Fields.Add(&core.NumberField{Name: "origin_lat"})
			ga.Fields.Add(&core.NumberField{Name: "origin_lng"})
			ga.Fields.Add(&core.NumberField{Name: "last_lat"})
			ga.Fields.Add(&core.NumberField{Name: "last_lng"})
			ga.Fields.Add(&core.DateField{Name: "started"})
			ga.Fields.Add(&core.DateField{Name: "reached_target_at"})
			ga.Fields.Add(&core.DateField{Name: "completed_at"})
			ga.Fields.Add(&core.DateField{Name: "last_checkin"})
			ga.Fields.Add(&core.AutodateField{
				Name:     "created",
				OnCreate: true,
			})
			ga.Fields.Add(&core.AutodateField{
				Name:     "updated",
				OnCreate: true,
				OnUpdate: true,
			})
			if err := app.Save(ga); err != nil {
				return err
			}
			geoAttemptsCol = ga
		}

		// coupons collection
		couponsCol, cerr := app.FindCollectionByNameOrId("coupons")
		if cerr != nil && !errors.Is(cerr, sql.ErrNoRows) {
			return cerr
		}
		if couponsCol == nil {
			cp := core.NewBaseCollection("coupons")

			// Public read so clients can display coupon preview on promoted challenges.
			cp.ListRule = types.Pointer("")
			cp.ViewRule = types.Pointer("")
			cp.CreateRule = nil
			cp.UpdateRule = nil
			cp.DeleteRule = nil

			cp.Fields.Add(&core.RelationField{
				Name:          "challenge",
				Required:      true,
				CollectionId:  geoChallengesCol.Id,
				MaxSelect:     1,
				CascadeDelete: true,
			})
			cp.Fields.Add(&core.TextField{
				Name:     "title",
				Required: true,
				Max:      200,
			})
			cp.Fields.Add(&core.TextField{
				Name:     "discount_text",
				Required: true,
				Max:      200,
			})
			cp.Fields.Add(&core.TextField{
				Name: "terms",
				Max:  2000,
			})
			cp.Fields.Add(&core.NumberField{
				Name:    "total_available",
				OnlyInt: true,
				Min:     types.Pointer(0.0),
			})
			cp.Fields.Add(&core.NumberField{
				Name:    "issued_count",
				OnlyInt: true,
				Min:     types.Pointer(0.0),
			})
			cp.Fields.Add(&core.DateField{Name: "expires_at"})
			cp.Fields.Add(&core.AutodateField{
				Name:     "created",
				OnCreate: true,
			})
			cp.Fields.Add(&core.AutodateField{
				Name:     "updated",
				OnCreate: true,
				OnUpdate: true,
			})
			if err := app.Save(cp); err != nil {
				return err
			}
			couponsCol = cp
		}

		// coupon_redemptions collection
		redempCol, rerr := app.FindCollectionByNameOrId("coupon_redemptions")
		if rerr != nil && !errors.Is(rerr, sql.ErrNoRows) {
			return rerr
		}
		if redempCol == nil {
			cr := core.NewBaseCollection("coupon_redemptions")

			// Only owner reads via collection API; partners use custom routes for their lookups.
			ownerRule := "user = @request.auth.id"
			cr.ListRule = types.Pointer(ownerRule)
			cr.ViewRule = types.Pointer(ownerRule)
			cr.CreateRule = nil
			cr.UpdateRule = nil
			cr.DeleteRule = nil

			cr.Indexes = append(cr.Indexes,
				"CREATE UNIQUE INDEX idx_coupon_redemptions_code ON coupon_redemptions (code)",
			)

			cr.Fields.Add(&core.RelationField{
				Name:          "user",
				Required:      true,
				CollectionId:  users.Id,
				MaxSelect:     1,
				CascadeDelete: true,
			})
			cr.Fields.Add(&core.RelationField{
				Name:          "coupon",
				Required:      true,
				CollectionId:  couponsCol.Id,
				MaxSelect:     1,
				CascadeDelete: true,
			})
			cr.Fields.Add(&core.RelationField{
				Name:          "attempt",
				Required:      false,
				CollectionId:  geoAttemptsCol.Id,
				MaxSelect:     1,
				CascadeDelete: false,
			})
			cr.Fields.Add(&core.TextField{
				Name:     "code",
				Required: true,
				Max:      16,
			})
			cr.Fields.Add(&core.BoolField{
				Name: "redeemed",
			})
			cr.Fields.Add(&core.DateField{
				Name: "redeemed_at",
			})
			cr.Fields.Add(&core.AutodateField{
				Name:     "created",
				OnCreate: true,
			})
			cr.Fields.Add(&core.AutodateField{
				Name:     "updated",
				OnCreate: true,
				OnUpdate: true,
			})
			if err := app.Save(cr); err != nil {
				return err
			}
		}

		return nil
	}, func(app core.App) error {
		for _, name := range []string{
			"coupon_redemptions",
			"coupons",
			"geo_attempts",
			"geo_challenges",
			"partners",
		} {
			if col, err := app.FindCollectionByNameOrId(name); err == nil && col != nil {
				if err := app.Delete(col); err != nil {
					return err
				}
			}
		}
		users, err := app.FindCollectionByNameOrId("users")
		if err != nil {
			return err
		}
		users.Fields.RemoveByName("is_partner")
		return app.Save(users)
	})
}
