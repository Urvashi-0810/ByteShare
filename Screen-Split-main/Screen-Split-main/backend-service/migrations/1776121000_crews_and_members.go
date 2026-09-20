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

		if _, cerr := app.FindCollectionByNameOrId("crews"); cerr == nil {
			return nil
		} else if !errors.Is(cerr, sql.ErrNoRows) {
			return cerr
		}

		crews := core.NewBaseCollection("crews")

		// Rules that reference crew_members cannot be set until that collection exists.
		// Phase 1: restrict client API; custom routes use app.Save with elevated access.
		crews.ListRule = nil
		crews.ViewRule = nil
		crews.UpdateRule = nil
		crews.CreateRule = types.Pointer(`@request.auth.id != ""`)
		crews.DeleteRule = types.Pointer(`@request.auth.id != "" && created_by ?= @request.auth.id`)

		crews.Indexes = append(crews.Indexes,
			"CREATE UNIQUE INDEX idx_crews_invite_code ON crews (invite_code)",
		)

		crews.Fields.Add(&core.TextField{
			Name:     "name",
			Required: true,
			Max:      200,
		})
		crews.Fields.Add(&core.TextField{
			Name:     "emoji",
			Required: true,
			Max:      16,
		})
		crews.Fields.Add(&core.TextField{
			Name:     "invite_code",
			Required: true,
			Max:      12,
		})
		crews.Fields.Add(&core.NumberField{
			Name:    "bill",
			Min:     types.Pointer(0.0),
			OnlyInt: false,
		})
		crews.Fields.Add(&core.RelationField{
			Name:         "created_by",
			Required:     true,
			CollectionId: users.Id,
			MaxSelect:    1,
			CascadeDelete: false,
		})
		crews.Fields.Add(&core.AutodateField{
			Name:     "created",
			OnCreate: true,
		})
		crews.Fields.Add(&core.AutodateField{
			Name:     "updated",
			OnCreate: true,
			OnUpdate: true,
		})

		if err := app.Save(crews); err != nil {
			return err
		}

		crewsCol, err := app.FindCollectionByNameOrId("crews")
		if err != nil {
			return err
		}

		cm := core.NewBaseCollection("crew_members")

		cmRowVisible := `@request.auth.id != "" && (member = @request.auth.id || (@collection.crew_members.crew ?= crew && @collection.crew_members.member ?= @request.auth.id))`
		cm.ListRule = types.Pointer(cmRowVisible)
		cm.ViewRule = types.Pointer(cmRowVisible)
		cm.CreateRule = nil
		cm.UpdateRule = nil
		cm.DeleteRule = nil

		cm.Indexes = append(cm.Indexes,
			"CREATE UNIQUE INDEX idx_crew_members_crew_member ON crew_members (crew, member)",
		)

		cm.Fields.Add(&core.RelationField{
			Name:          "crew",
			Required:      true,
			CollectionId:  crewsCol.Id,
			MaxSelect:     1,
			CascadeDelete: true,
		})
		cm.Fields.Add(&core.RelationField{
			Name:          "member",
			Required:      true,
			CollectionId:  users.Id,
			MaxSelect:     1,
			CascadeDelete: true,
		})
		cm.Fields.Add(&core.AutodateField{
			Name:     "created",
			OnCreate: true,
		})
		cm.Fields.Add(&core.AutodateField{
			Name:     "updated",
			OnCreate: true,
			OnUpdate: true,
		})

		if err := app.Save(cm); err != nil {
			return err
		}

		// Phase 3: crew_members exists; safe to attach cross-collection rules on crews.
		memberVisible := `@request.auth.id != "" && @collection.crew_members.crew ?= id && @collection.crew_members.member ?= @request.auth.id`
		crewsCol.ListRule = types.Pointer(memberVisible)
		crewsCol.ViewRule = types.Pointer(memberVisible)
		crewsCol.UpdateRule = types.Pointer(memberVisible)
		return app.Save(crewsCol)
	}, func(app core.App) error {
		if col, err := app.FindCollectionByNameOrId("crew_members"); err == nil && col != nil {
			if err := app.Delete(col); err != nil {
				return err
			}
		}
		if col, err := app.FindCollectionByNameOrId("crews"); err == nil && col != nil {
			if err := app.Delete(col); err != nil {
				return err
			}
		}
		return nil
	})
}
