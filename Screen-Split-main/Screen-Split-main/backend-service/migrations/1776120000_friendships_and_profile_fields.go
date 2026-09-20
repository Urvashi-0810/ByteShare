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

		if users.Fields.GetByName("avatar_emoji") == nil {
			users.Fields.Add(&core.TextField{
				Name: "avatar_emoji",
				Max:  16,
			})
		}
		if users.Fields.GetByName("streak") == nil {
			users.Fields.Add(&core.NumberField{
				Name:    "streak",
				OnlyInt: true,
				Min:     types.Pointer(0.0),
			})
		}
		if users.Fields.GetByName("badges") == nil {
			users.Fields.Add(&core.JSONField{
				Name: "badges",
			})
		}
		if users.Fields.GetByName("bio") == nil {
			users.Fields.Add(&core.TextField{
				Name: "bio",
				Max:  2000,
			})
		}
		if users.Fields.GetByName("analytics") == nil {
			users.Fields.Add(&core.JSONField{
				Name: "analytics",
			})
		}

		if err := app.Save(users); err != nil {
			return err
		}

		if _, ferr := app.FindCollectionByNameOrId("friendships"); ferr == nil {
			return nil
		} else if !errors.Is(ferr, sql.ErrNoRows) {
			return ferr
		}

		friendships := core.NewBaseCollection("friendships")

		edgeRule := "owner = @request.auth.id || peer = @request.auth.id"
		friendships.ListRule = types.Pointer(edgeRule)
		friendships.ViewRule = types.Pointer(edgeRule)
		friendships.CreateRule = nil
		friendships.UpdateRule = nil
		friendships.DeleteRule = types.Pointer(edgeRule)

		friendships.Fields.Add(&core.RelationField{
			Name:         "owner",
			Required:     true,
			CollectionId: users.Id,
			MaxSelect:    1,
			CascadeDelete: true,
		})
		friendships.Fields.Add(&core.RelationField{
			Name:         "peer",
			Required:     true,
			CollectionId: users.Id,
			MaxSelect:    1,
			CascadeDelete: true,
		})
		friendships.Fields.Add(&core.AutodateField{
			Name:     "created",
			OnCreate: true,
		})
		friendships.Fields.Add(&core.AutodateField{
			Name:     "updated",
			OnCreate: true,
			OnUpdate: true,
		})

		return app.Save(friendships)
	}, func(app core.App) error {
		col, err := app.FindCollectionByNameOrId("friendships")
		if err == nil && col != nil {
			if err := app.Delete(col); err != nil {
				return err
			}
		}

		users, err := app.FindCollectionByNameOrId("users")
		if err != nil {
			return err
		}
		users.Fields.RemoveByName("avatar_emoji")
		users.Fields.RemoveByName("streak")
		users.Fields.RemoveByName("badges")
		users.Fields.RemoveByName("bio")
		users.Fields.RemoveByName("analytics")
		return app.Save(users)
	})
}
