package routes

import (
	"net/http"
	"strings"
	"time"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

const (
	frPending    = "pending"
	frAccepted   = "accepted"
	frRejected   = "rejected"
	frCancelled  = "cancelled"
	frCollection = "friend_requests"
)

type analyticsPoint struct {
	Date         string `json:"date"`
	UsageMinutes int    `json:"usageMinutes"`
}

type friendUser struct {
	ID        string           `json:"id"`
	Name      string           `json:"name"`
	Email     string           `json:"email"`
	Avatar    string           `json:"avatar"`
	Streak    int              `json:"streak"`
	Badges    []string         `json:"badges"`
	Analytics []analyticsPoint `json:"analytics"`
	Bio       string           `json:"bio"`
	JoinDate  string           `json:"joinDate"`
}

type friendEdge struct {
	FriendshipID string     `json:"friendshipId"`
	User         friendUser `json:"user"`
}

type friendsListResponse struct {
	Items []friendEdge `json:"items"`
}

type requestByEmailBody struct {
	Email string `json:"email"`
}

type incomingItem struct {
	ID       string     `json:"id"`
	FromUser friendUser `json:"fromUser"`
	Created  string     `json:"created"`
}

type incomingListResponse struct {
	Items []incomingItem `json:"items"`
}

type outgoingItem struct {
	ID     string     `json:"id"`
	ToUser friendUser `json:"toUser"`
	Created string    `json:"created"`
}

type outgoingListResponse struct {
	Items []outgoingItem `json:"items"`
}

func mapUserRecord(rec *core.Record) friendUser {
	u := friendUser{
		ID:     rec.Id,
		Name:   rec.GetString("name"),
		Email:  rec.GetString("email"),
		Avatar: strings.TrimSpace(rec.GetString("avatar_emoji")),
		Streak: rec.GetInt("streak"),
		Bio:    rec.GetString("bio"),
	}
	if u.Avatar == "" {
		u.Avatar = "👤"
	}

	_ = rec.UnmarshalJSONField("badges", &u.Badges)
	if u.Badges == nil {
		u.Badges = []string{}
	}

	_ = rec.UnmarshalJSONField("analytics", &u.Analytics)
	if u.Analytics == nil {
		u.Analytics = []analyticsPoint{}
	}

	if created := rec.GetString("created"); created != "" {
		layouts := []string{
			"2006-01-02 15:04:05.000Z",
			"2006-01-02 15:04:05Z",
			time.RFC3339,
		}
		for _, layout := range layouts {
			if t, err := time.Parse(layout, created); err == nil {
				u.JoinDate = t.UTC().Format("January 2006")
				break
			}
		}
	}
	if u.JoinDate == "" {
		u.JoinDate = "—"
	}

	return u
}

func findFriendshipPair(app core.App, a, b string) (*core.Record, error) {
	records, err := app.FindRecordsByFilter(
		"friendships",
		"(owner = {:a} && peer = {:b}) || (owner = {:b} && peer = {:a})",
		"",
		1,
		0,
		dbx.Params{"a": a, "b": b},
	)
	if err != nil {
		return nil, err
	}
	if len(records) == 0 {
		return nil, nil
	}
	return records[0], nil
}

func findFriendRequest(app core.App, fromID, toID, status string) (*core.Record, error) {
	records, err := app.FindRecordsByFilter(
		frCollection,
		"from = {:from} && to = {:to} && status = {:st}",
		"",
		1,
		0,
		dbx.Params{"from": fromID, "to": toID, "st": status},
	)
	if err != nil {
		return nil, err
	}
	if len(records) == 0 {
		return nil, nil
	}
	return records[0], nil
}

func ensureFriendship(app core.App, ownerID, peerID string) (*core.Record, bool, error) {
	existing, err := findFriendshipPair(app, ownerID, peerID)
	if err != nil {
		return nil, false, err
	}
	if existing != nil {
		return existing, false, nil
	}
	col, err := app.FindCollectionByNameOrId("friendships")
	if err != nil {
		return nil, false, err
	}
	rec := core.NewRecord(col)
	rec.Set("owner", ownerID)
	rec.Set("peer", peerID)
	if err := app.Save(rec); err != nil {
		return nil, false, err
	}
	return rec, true, nil
}

func loadFriendRequest(app core.App, id string) (*core.Record, error) {
	return app.FindRecordById(frCollection, id)
}

// RegisterFriendRoutes registers authenticated friend list and request endpoints.
func RegisterFriendRoutes(se *core.ServeEvent) {
	se.Router.GET("/friends", listFriends).Bind(apis.RequireAuth("users"))
	se.Router.POST("/friends/request-by-email", requestFriendByEmail).Bind(apis.RequireAuth("users"))
	se.Router.GET("/friends/incoming-requests", listIncomingRequests).Bind(apis.RequireAuth("users"))
	se.Router.GET("/friends/outgoing-requests", listOutgoingRequests).Bind(apis.RequireAuth("users"))
	se.Router.POST("/friends/requests/{id}/accept", acceptFriendRequest).Bind(apis.RequireAuth("users"))
	se.Router.POST("/friends/requests/{id}/reject", rejectFriendRequest).Bind(apis.RequireAuth("users"))
	se.Router.POST("/friends/requests/{id}/cancel", cancelFriendRequest).Bind(apis.RequireAuth("users"))
	se.Router.POST("/friends/{id}/remove", removeFriend).Bind(apis.RequireAuth("users"))
}

func removeFriend(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}
	id := re.Request.PathValue("id")
	if id == "" {
		return re.NotFoundError("missing friendship id", nil)
	}
	rec, err := re.App.FindRecordById("friendships", id)
	if err != nil || rec == nil {
		return re.NotFoundError("friendship not found", nil)
	}
	ownerID := rec.GetString("owner")
	peerID := rec.GetString("peer")
	if ownerID != me.Id && peerID != me.Id {
		return re.ForbiddenError("not allowed to remove this friendship", nil)
	}
	if err := re.App.Delete(rec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, map[string]any{"ok": true})
}

func listFriends(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	edges, err := re.App.FindRecordsByFilter(
		"friendships",
		"owner = {:id} || peer = {:id}",
		"created",
		500,
		0,
		dbx.Params{"id": me.Id},
	)
	if err != nil {
		return err
	}

	out := make([]friendEdge, 0, len(edges))
	for _, edge := range edges {
		ownerID := edge.GetString("owner")
		peerID := edge.GetString("peer")
		friendID := peerID
		if peerID == me.Id {
			friendID = ownerID
		}
		if friendID == me.Id {
			continue
		}
		fRec, err := re.App.FindRecordById("users", friendID)
		if err != nil || fRec == nil {
			continue
		}
		out = append(out, friendEdge{
			FriendshipID: edge.Id,
			User:         mapUserRecord(fRec),
		})
	}

	return re.JSON(http.StatusOK, friendsListResponse{Items: out})
}

func requestFriendByEmail(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	var body requestByEmailBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}
	email := strings.TrimSpace(body.Email)
	if email == "" {
		return re.BadRequestError("email is required", nil)
	}

	target, err := re.App.FindAuthRecordByEmail("users", email)
	if err != nil || target == nil {
		return re.NotFoundError("no user with that email", nil)
	}
	if target.Id == me.Id {
		return re.BadRequestError("cannot add yourself", nil)
	}

	if fr, err := findFriendshipPair(re.App, me.Id, target.Id); err != nil {
		return err
	} else if fr != nil {
		return re.JSON(http.StatusOK, map[string]any{
			"status":         "already_friends",
			"friendshipId":   fr.Id,
			"user":           mapUserRecord(target),
		})
	}

	if pendingOut, err := findFriendRequest(re.App, me.Id, target.Id, frPending); err != nil {
		return err
	} else if pendingOut != nil {
		return re.JSON(http.StatusOK, map[string]any{
			"status":    "request_pending",
			"requestId": pendingOut.Id,
			"user":      mapUserRecord(target),
		})
	}

	if pendingIn, err := findFriendRequest(re.App, target.Id, me.Id, frPending); err != nil {
		return err
	} else if pendingIn != nil {
		friendRec, _, err := ensureFriendship(re.App, target.Id, me.Id)
		if err != nil {
			return err
		}
		pendingIn.Set("status", frAccepted)
		if err := re.App.Save(pendingIn); err != nil {
			return err
		}
		return re.JSON(http.StatusOK, map[string]any{
			"status":         "now_friends",
			"friendshipId":   friendRec.Id,
			"user":           mapUserRecord(target),
			"resolvedRequestId": pendingIn.Id,
		})
	}

	col, err := re.App.FindCollectionByNameOrId(frCollection)
	if err != nil {
		return err
	}
	rec := core.NewRecord(col)
	rec.Set("from", me.Id)
	rec.Set("to", target.Id)
	rec.Set("status", frPending)
	if err := re.App.Save(rec); err != nil {
		return err
	}

	return re.JSON(http.StatusOK, map[string]any{
		"status":    "created",
		"requestId": rec.Id,
		"user":      mapUserRecord(target),
	})
}

func listIncomingRequests(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	records, err := re.App.FindRecordsByFilter(
		frCollection,
		"to = {:id} && status = {:st}",
		"created",
		200,
		0,
		dbx.Params{"id": me.Id, "st": frPending},
	)
	if err != nil {
		return err
	}

	out := make([]incomingItem, 0, len(records))
	for _, r := range records {
		fromID := r.GetString("from")
		fromRec, err := re.App.FindRecordById("users", fromID)
		if err != nil || fromRec == nil {
			continue
		}
		out = append(out, incomingItem{
			ID:       r.Id,
			FromUser: mapUserRecord(fromRec),
			Created:  r.GetString("created"),
		})
	}

	return re.JSON(http.StatusOK, incomingListResponse{Items: out})
}

func listOutgoingRequests(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	records, err := re.App.FindRecordsByFilter(
		frCollection,
		"from = {:id} && status = {:st}",
		"-created",
		200,
		0,
		dbx.Params{"id": me.Id, "st": frPending},
	)
	if err != nil {
		return err
	}

	out := make([]outgoingItem, 0, len(records))
	for _, r := range records {
		toID := r.GetString("to")
		toRec, err := re.App.FindRecordById("users", toID)
		if err != nil || toRec == nil {
			continue
		}
		out = append(out, outgoingItem{
			ID:      r.Id,
			ToUser:  mapUserRecord(toRec),
			Created: r.GetString("created"),
		})
	}

	return re.JSON(http.StatusOK, outgoingListResponse{Items: out})
}

func acceptFriendRequest(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}
	id := re.Request.PathValue("id")
	if id == "" {
		return re.NotFoundError("missing request id", nil)
	}

	rec, err := loadFriendRequest(re.App, id)
	if err != nil || rec == nil {
		return re.NotFoundError("request not found", nil)
	}
	if rec.GetString("to") != me.Id {
		return re.ForbiddenError("not allowed to accept this request", nil)
	}
	if rec.GetString("status") != frPending {
		return re.BadRequestError("request is not pending", nil)
	}

	fromID := rec.GetString("from")
	toID := rec.GetString("to")
	friendRec, _, err := ensureFriendship(re.App, fromID, toID)
	if err != nil {
		return err
	}

	rec.Set("status", frAccepted)
	if err := re.App.Save(rec); err != nil {
		return err
	}

	peer, err := re.App.FindRecordById("users", fromID)
	if err != nil || peer == nil {
		return err
	}

	return re.JSON(http.StatusOK, map[string]any{
		"friendshipId": friendRec.Id,
		"user":         mapUserRecord(peer),
	})
}

func rejectFriendRequest(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}
	id := re.Request.PathValue("id")
	if id == "" {
		return re.NotFoundError("missing request id", nil)
	}

	rec, err := loadFriendRequest(re.App, id)
	if err != nil || rec == nil {
		return re.NotFoundError("request not found", nil)
	}
	if rec.GetString("to") != me.Id {
		return re.ForbiddenError("not allowed to reject this request", nil)
	}
	if rec.GetString("status") != frPending {
		return re.BadRequestError("request is not pending", nil)
	}

	rec.Set("status", frRejected)
	if err := re.App.Save(rec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, map[string]any{"ok": true})
}

func cancelFriendRequest(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}
	id := re.Request.PathValue("id")
	if id == "" {
		return re.NotFoundError("missing request id", nil)
	}

	rec, err := loadFriendRequest(re.App, id)
	if err != nil || rec == nil {
		return re.NotFoundError("request not found", nil)
	}
	if rec.GetString("from") != me.Id {
		return re.ForbiddenError("not allowed to cancel this request", nil)
	}
	if rec.GetString("status") != frPending {
		return re.BadRequestError("request is not pending", nil)
	}

	rec.Set("status", frCancelled)
	if err := re.App.Save(rec); err != nil {
		return err
	}
	return re.JSON(http.StatusOK, map[string]any{"ok": true})
}
