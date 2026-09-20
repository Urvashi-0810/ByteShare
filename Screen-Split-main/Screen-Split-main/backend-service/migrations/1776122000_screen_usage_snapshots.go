package migrations

import (
	"database/sql"
	"errors"

	"github.com/pocketbase/pocketbase/core"
	m "github.com/pocketbase/pocketbase/migrations"
)

func init() {
	m.Register(func(app core.App) error {
		users, err := app.FindCollectionByNameOrId("users")
		if err != nil {
			return err
		}

		if _, cerr := app.FindCollectionByNameOrId("screen_usage_snapshots"); cerr == nil {
			return nil
		} else if !errors.Is(cerr, sql.ErrNoRows) {
			return cerr
		}

		col := core.NewBaseCollection("screen_usage_snapshots")

		// All access via custom routes (elevated Save), same pattern as crews phase 1.
		col.ListRule = nil
		col.ViewRule = nil
		col.CreateRule = nil
		col.UpdateRule = nil
		col.DeleteRule = nil

		col.Indexes = append(col.Indexes,
			"CREATE UNIQUE INDEX idx_screen_usage_user_day ON screen_usage_snapshots (user, report_date)",
		)

		col.Fields.Add(&core.RelationField{
			Name:          "user",
			Required:      true,
			CollectionId:  users.Id,
			MaxSelect:     1,
			CascadeDelete: true,
		})
		col.Fields.Add(&core.TextField{
			Name:     "report_date",
			Required: true,
			Max:      10,
		})
		col.Fields.Add(&core.JSONField{
			Name: "apps",
		})
		col.Fields.Add(&core.TextField{
			Name: "source",
			Max:  64,
		})
		col.Fields.Add(&core.AutodateField{
			Name:     "created",
			OnCreate: true,
		})
		col.Fields.Add(&core.AutodateField{
			Name:     "updated",
			OnCreate: true,
			OnUpdate: true,
		})

		return app.Save(col)
	}, func(app core.App) error {
		col, err := app.FindCollectionByNameOrId("screen_usage_snapshots")
		if err == nil && col != nil {
			return app.Delete(col)
		}
		return nil
	})
}
