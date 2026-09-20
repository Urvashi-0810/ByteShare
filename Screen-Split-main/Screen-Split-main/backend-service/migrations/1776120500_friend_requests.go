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

		if _, ferr := app.FindCollectionByNameOrId("friend_requests"); ferr == nil {
			return nil
		} else if !errors.Is(ferr, sql.ErrNoRows) {
			return ferr
		}

		fr := core.NewBaseCollection("friend_requests")

		participantRule := "from = @request.auth.id || to = @request.auth.id"
		fr.ListRule = types.Pointer(participantRule)
		fr.ViewRule = types.Pointer(participantRule)
		fr.CreateRule = nil
		fr.UpdateRule = nil
		fr.DeleteRule = nil

		fr.Fields.Add(&core.RelationField{
			Name:          "from",
			Required:      true,
			CollectionId:  users.Id,
			MaxSelect:     1,
			CascadeDelete: true,
		})
		fr.Fields.Add(&core.RelationField{
			Name:          "to",
			Required:      true,
			CollectionId:  users.Id,
			MaxSelect:     1,
			CascadeDelete: true,
		})
		fr.Fields.Add(&core.SelectField{
			Name:     "status",
			Required: true,
			Values:   []string{"pending", "accepted", "rejected", "cancelled"},
			MaxSelect: 1,
		})
		fr.Fields.Add(&core.AutodateField{
			Name:     "created",
			OnCreate: true,
		})
		fr.Fields.Add(&core.AutodateField{
			Name:     "updated",
			OnCreate: true,
			OnUpdate: true,
		})

		return app.Save(fr)
	}, func(app core.App) error {
		col, err := app.FindCollectionByNameOrId("friend_requests")
		if err == nil && col != nil {
			return app.Delete(col)
		}
		return nil
	})
}
