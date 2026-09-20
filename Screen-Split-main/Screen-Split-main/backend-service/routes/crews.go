package routes

import (
	"crypto/rand"
	"net/http"
	"strings"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

const inviteAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

type crewDTO struct {
	ID         string       `json:"id"`
	Name       string       `json:"name"`
	Emoji      string       `json:"emoji"`
	InviteCode string       `json:"inviteCode"`
	Bill       float64      `json:"bill"`
	MemberIDs  []string     `json:"memberIds"`
	Members    []friendUser `json:"members"`
}

type crewsListResponse struct {
	Items []crewDTO `json:"items"`
}

type createCrewBody struct {
	Name  string  `json:"name"`
	Emoji string  `json:"emoji"`
	Bill  float64 `json:"bill"`
}

type joinCrewBody struct {
	Code string `json:"code"`
}

func randomInviteCode(n int) string {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		for i := range b {
			b[i] = inviteAlphabet[i%len(inviteAlphabet)]
		}
		return string(b)
	}
	for i := range b {
		b[i] = inviteAlphabet[int(b[i])%len(inviteAlphabet)]
	}
	return string(b)
}

func crewToDTO(crewRec *core.Record, memberIDs []string, members []friendUser) crewDTO {
	bill := crewRec.GetFloat("bill")
	return crewDTO{
		ID:         crewRec.Id,
		Name:       crewRec.GetString("name"),
		Emoji:      crewRec.GetString("emoji"),
		InviteCode: crewRec.GetString("invite_code"),
		Bill:       bill,
		MemberIDs:  memberIDs,
		Members:    members,
	}
}

func memberProfilesForUserIDs(app core.App, ids []string) []friendUser {
	out := make([]friendUser, 0, len(ids))
	for _, id := range ids {
		if id == "" {
			continue
		}
		rec, err := app.FindRecordById("users", id)
		if err != nil || rec == nil {
			continue
		}
		out = append(out, mapUserRecord(rec))
	}
	return out
}

func memberIDsForCrew(app core.App, crewID string) ([]string, error) {
	rows, err := app.FindRecordsByFilter(
		"crew_members",
		"crew = {:id}",
		"created",
		500,
		0,
		dbx.Params{"id": crewID},
	)
	if err != nil {
		return nil, err
	}
	out := make([]string, 0, len(rows))
	for _, r := range rows {
		out = append(out, r.GetString("member"))
	}
	return out, nil
}

func loadCrewDTO(app core.App, crewRec *core.Record) (crewDTO, error) {
	ids, err := memberIDsForCrew(app, crewRec.Id)
	if err != nil {
		return crewDTO{}, err
	}
	members := memberProfilesForUserIDs(app, ids)
	return crewToDTO(crewRec, ids, members), nil
}

// RegisterCrewRoutes registers authenticated crew list, create, and join endpoints.
func RegisterCrewRoutes(se *core.ServeEvent) {
	se.Router.GET("/crews/mine", listMyCrews).Bind(apis.RequireAuth("users"))
	se.Router.POST("/crews", createCrew).Bind(apis.RequireAuth("users"))
	se.Router.POST("/crews/join", joinCrew).Bind(apis.RequireAuth("users"))
}

func listMyCrews(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	memberships, err := re.App.FindRecordsByFilter(
		"crew_members",
		"member = {:id}",
		"created",
		500,
		0,
		dbx.Params{"id": me.Id},
	)
	if err != nil {
		return err
	}

	items := make([]crewDTO, 0, len(memberships))
	seen := make(map[string]struct{})
	for _, mrec := range memberships {
		cid := mrec.GetString("crew")
		if cid == "" {
			continue
		}
		if _, ok := seen[cid]; ok {
			continue
		}
		seen[cid] = struct{}{}
		crewRec, err := re.App.FindRecordById("crews", cid)
		if err != nil || crewRec == nil {
			continue
		}
		dto, err := loadCrewDTO(re.App, crewRec)
		if err != nil {
			return err
		}
		items = append(items, dto)
	}

	return re.JSON(http.StatusOK, crewsListResponse{Items: items})
}

func createCrew(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	var body createCrewBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}
	name := strings.TrimSpace(body.Name)
	if name == "" {
		return re.BadRequestError("name is required", nil)
	}
	emoji := strings.TrimSpace(body.Emoji)
	if emoji == "" {
		emoji = "🍕"
	}
	bill := body.Bill
	if bill < 0 {
		bill = 0
	}

	col, err := re.App.FindCollectionByNameOrId("crews")
	if err != nil {
		return err
	}

	var crewRec *core.Record
	for attempt := 0; attempt < 8; attempt++ {
		rec := core.NewRecord(col)
		rec.Set("name", name)
		rec.Set("emoji", emoji)
		rec.Set("invite_code", strings.ToUpper(randomInviteCode(6)))
		rec.Set("bill", bill)
		rec.Set("created_by", me.Id)
		if err := re.App.Save(rec); err != nil {
			if strings.Contains(strings.ToLower(err.Error()), "unique") {
				continue
			}
			return err
		}
		crewRec = rec
		break
	}
	if crewRec == nil {
		return re.BadRequestError("could not allocate invite code", nil)
	}

	cmCol, err := re.App.FindCollectionByNameOrId("crew_members")
	if err != nil {
		return err
	}
	memberRec := core.NewRecord(cmCol)
	memberRec.Set("crew", crewRec.Id)
	memberRec.Set("member", me.Id)
	if err := re.App.Save(memberRec); err != nil {
		return err
	}

	dto, err := loadCrewDTO(re.App, crewRec)
	if err != nil {
		return err
	}
	return re.JSON(http.StatusOK, dto)
}

func joinCrew(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	var body joinCrewBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}
	code := strings.TrimSpace(strings.ToUpper(body.Code))
	if code == "" {
		return re.BadRequestError("code is required", nil)
	}

	records, err := re.App.FindRecordsByFilter(
		"crews",
		"invite_code = {:c}",
		"",
		1,
		0,
		dbx.Params{"c": code},
	)
	if err != nil {
		return err
	}
	if len(records) == 0 {
		return re.NotFoundError("no crew with that code", nil)
	}
	crewRec := records[0]

	existing, err := re.App.FindRecordsByFilter(
		"crew_members",
		"crew = {:crew} && member = {:member}",
		"",
		1,
		0,
		dbx.Params{"crew": crewRec.Id, "member": me.Id},
	)
	if err != nil {
		return err
	}
	if len(existing) > 0 {
		dto, err := loadCrewDTO(re.App, crewRec)
		if err != nil {
			return err
		}
		return re.JSON(http.StatusOK, map[string]any{
			"status": "already_member",
			"crew":   dto,
		})
	}

	cmCol, err := re.App.FindCollectionByNameOrId("crew_members")
	if err != nil {
		return err
	}
	memberRec := core.NewRecord(cmCol)
	memberRec.Set("crew", crewRec.Id)
	memberRec.Set("member", me.Id)
	if err := re.App.Save(memberRec); err != nil {
		return err
	}

	dto, err := loadCrewDTO(re.App, crewRec)
	if err != nil {
		return err
	}
	return re.JSON(http.StatusOK, map[string]any{
		"status": "joined",
		"crew":   dto,
	})
}
