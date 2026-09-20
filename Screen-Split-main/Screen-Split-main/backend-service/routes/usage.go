package routes

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
	"github.com/pocketbase/pocketbase/tools/types"
)

const screenUsageCollection = "screen_usage_snapshots"

type appUsageEntry struct {
	AppName  string  `json:"appName"`
	Category string  `json:"category"`
	Minutes  float64 `json:"minutes"`
}

type usageSnapshotDTO struct {
	UserID     string          `json:"userId"`
	Apps       []appUsageEntry `json:"apps"`
	Updated    string          `json:"updated,omitempty"`
	ReportDate string          `json:"reportDate,omitempty"`
}

type socialUsageResponse struct {
	ReportDate string             `json:"reportDate"`
	Items      []usageSnapshotDTO `json:"items"`
}

type upsertMyUsageBody struct {
	ReportDate string          `json:"reportDate"`
	Apps       []appUsageEntry `json:"apps"`
	Source     string          `json:"source"`
}

type adminUpsertBody struct {
	UserID     string          `json:"userId"`
	ReportDate string          `json:"reportDate"`
	Apps       []appUsageEntry `json:"apps"`
	Source     string          `json:"source"`
}

var allowedCategories = map[string]struct{}{
	"social":     {},
	"stream":     {},
	"neutral":    {},
	"productive": {},
}

// ErrInvalidUsage is returned when app usage JSON fails validation.
var ErrInvalidUsage = errors.New("invalid screen usage")

func normalizeReportDate(s string) string {
	s = strings.TrimSpace(s)
	if s == "" {
		return time.Now().UTC().Format("2006-01-02")
	}
	if _, err := time.Parse("2006-01-02", s); err != nil {
		return ""
	}
	return s
}

func validateApps(entries []appUsageEntry) error {
	if len(entries) > 200 {
		return fmt.Errorf("%w: at most 200 app rows", ErrInvalidUsage)
	}
	for _, e := range entries {
		if strings.TrimSpace(e.AppName) == "" {
			return fmt.Errorf("%w: appName is required", ErrInvalidUsage)
		}
		if len(e.AppName) > 200 {
			return fmt.Errorf("%w: appName too long", ErrInvalidUsage)
		}
		if _, ok := allowedCategories[e.Category]; !ok {
			return fmt.Errorf("%w: invalid category", ErrInvalidUsage)
		}
		if e.Minutes < 0 || e.Minutes > 10080 {
			return fmt.Errorf("%w: minutes out of range", ErrInvalidUsage)
		}
	}
	return nil
}

func marshalApps(entries []appUsageEntry) (types.JSONRaw, error) {
	b, err := json.Marshal(entries)
	if err != nil {
		return nil, err
	}
	return types.JSONRaw(b), nil
}

func upsertSnapshot(app core.App, userID, reportDate string, apps []appUsageEntry, source string) (*core.Record, error) {
	if err := validateApps(apps); err != nil {
		return nil, err
	}
	raw, err := marshalApps(apps)
	if err != nil {
		return nil, err
	}

	col, err := app.FindCollectionByNameOrId(screenUsageCollection)
	if err != nil {
		return nil, err
	}

	records, err := app.FindRecordsByFilter(
		screenUsageCollection,
		"user = {:u} && report_date = {:d}",
		"",
		1,
		0,
		dbx.Params{"u": userID, "d": reportDate},
	)
	if err != nil {
		return nil, err
	}

	src := strings.TrimSpace(source)
	if src == "" {
		src = "api"
	}
	if len(src) > 64 {
		src = src[:64]
	}

	var rec *core.Record
	if len(records) > 0 {
		rec = records[0]
	} else {
		rec = core.NewRecord(col)
		rec.Set("user", userID)
		rec.Set("report_date", reportDate)
	}
	rec.Set("apps", raw)
	rec.Set("source", src)
	if err := app.Save(rec); err != nil {
		return nil, err
	}
	return rec, nil
}

func snapshotToDTO(rec *core.Record, reportDate string) usageSnapshotDTO {
	uid := rec.GetString("user")
	var apps []appUsageEntry
	_ = rec.UnmarshalJSONField("apps", &apps)
	if apps == nil {
		apps = []appUsageEntry{}
	}
	return usageSnapshotDTO{
		UserID:     uid,
		Apps:       apps,
		Updated:    rec.GetString("updated"),
		ReportDate: reportDate,
	}
}

// RegisterUsageRoutes registers screen usage endpoints for Social Rank and device sync.
func RegisterUsageRoutes(se *core.ServeEvent) {
	se.Router.GET("/usage/social", socialUsage).Bind(apis.RequireAuth("users"))
	se.Router.POST("/usage/me", upsertMyUsage).Bind(apis.RequireAuth("users"))
	se.Router.POST("/usage/admin", adminUpsertUsage)
}

func socialUsage(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	reportDate := normalizeReportDate(re.Request.URL.Query().Get("date"))
	if reportDate == "" {
		return re.BadRequestError("invalid date", nil)
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

	memberIDs := []string{me.Id}
	seen := map[string]struct{}{me.Id: {}}
	for _, edge := range edges {
		ownerID := edge.GetString("owner")
		peerID := edge.GetString("peer")
		fid := peerID
		if peerID == me.Id {
			fid = ownerID
		}
		if fid == me.Id {
			continue
		}
		if _, ok := seen[fid]; ok {
			continue
		}
		seen[fid] = struct{}{}
		memberIDs = append(memberIDs, fid)
	}

	items := make([]usageSnapshotDTO, 0, len(memberIDs))
	for _, uid := range memberIDs {
		records, err := re.App.FindRecordsByFilter(
			screenUsageCollection,
			"user = {:u} && report_date = {:d}",
			"",
			1,
			0,
			dbx.Params{"u": uid, "d": reportDate},
		)
		if err != nil {
			return err
		}
		if len(records) == 0 {
			items = append(items, usageSnapshotDTO{
				UserID:     uid,
				Apps:       []appUsageEntry{},
				ReportDate: reportDate,
			})
			continue
		}
		items = append(items, snapshotToDTO(records[0], reportDate))
	}

	return re.JSON(http.StatusOK, socialUsageResponse{
		ReportDate: reportDate,
		Items:      items,
	})
}

func upsertMyUsage(re *core.RequestEvent) error {
	me := re.Auth
	if me == nil {
		return re.UnauthorizedError("missing auth", nil)
	}

	var body upsertMyUsageBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}
	reportDate := normalizeReportDate(body.ReportDate)
	if reportDate == "" {
		return re.BadRequestError("invalid reportDate", nil)
	}

	rec, err := upsertSnapshot(re.App, me.Id, reportDate, body.Apps, body.Source)
	if err != nil {
		if errors.Is(err, ErrInvalidUsage) {
			return re.BadRequestError(err.Error(), nil)
		}
		return err
	}
	return re.JSON(http.StatusOK, snapshotToDTO(rec, reportDate))
}

func adminUpsertUsage(re *core.RequestEvent) error {
	secret := strings.TrimSpace(os.Getenv("SCREEN_USAGE_ADMIN_SECRET"))
	if secret == "" {
		return re.ForbiddenError("admin usage endpoint disabled", nil)
	}
	if strings.TrimSpace(re.Request.Header.Get("X-Screen-Usage-Admin-Secret")) != secret {
		return re.UnauthorizedError("invalid admin secret", nil)
	}

	var body adminUpsertBody
	if err := re.BindBody(&body); err != nil {
		return re.BadRequestError("invalid JSON body", err)
	}
	userID := strings.TrimSpace(body.UserID)
	if userID == "" {
		return re.BadRequestError("userId is required", nil)
	}
	if _, err := re.App.FindRecordById("users", userID); err != nil {
		return re.NotFoundError("user not found", err)
	}
	reportDate := normalizeReportDate(body.ReportDate)
	if reportDate == "" {
		return re.BadRequestError("invalid reportDate", nil)
	}
	src := strings.TrimSpace(body.Source)
	if src == "" {
		src = "admin_dashboard"
	}

	rec, err := upsertSnapshot(re.App, userID, reportDate, body.Apps, src)
	if err != nil {
		if errors.Is(err, ErrInvalidUsage) {
			return re.BadRequestError(err.Error(), nil)
		}
		return err
	}
	return re.JSON(http.StatusOK, snapshotToDTO(rec, reportDate))
}
