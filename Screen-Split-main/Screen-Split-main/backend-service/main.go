package main

import (
	"fmt"
	"log"

	"github.com/joho/godotenv"
	"github.com/pocketbase/pocketbase"
	"github.com/pocketbase/pocketbase/core"
	m "github.com/pocketbase/pocketbase/migrations"
	"github.com/pocketbase/pocketbase/plugins/migratecmd"
	"github.com/pocketbase/pocketbase/tools/osutils"

	"github.com/anuragpandey01/summer-hacks-backend-service/config"
	_ "github.com/anuragpandey01/summer-hacks-backend-service/migrations"
	"github.com/anuragpandey01/summer-hacks-backend-service/routes"
)

func main() {
	app := pocketbase.New()

	_ = godotenv.Load()

	m.Register(func(app core.App) error {

		cfg, err := config.LoadSettings("config/config.yml")
		if err != nil {
			return fmt.Errorf("failed to load settings: %w", err)
		}
		s := app.Settings()

		s.Meta.AppName = cfg.Meta.AppName
		s.Meta.AppURL = cfg.Meta.AppURL
		s.Meta.SenderName = cfg.Meta.SenderName
		s.Meta.SenderAddress = cfg.Meta.SenderAddress
		s.Meta.HideControls = cfg.Meta.HideControls

		s.SMTP.Enabled = cfg.SMTP.Enabled
		s.SMTP.Host = cfg.SMTP.Host
		s.SMTP.Port = cfg.SMTP.Port
		s.SMTP.Username = cfg.SMTP.Username
		s.SMTP.Password = cfg.SMTP.Password
		s.SMTP.TLS = cfg.SMTP.TLS

		s.Logs.MaxDays = cfg.Logs.MaxDays
		s.Logs.LogIP = cfg.Logs.LogIP

		s.S3.Enabled = cfg.S3.Enabled
		s.S3.Endpoint = cfg.S3.Endpoint
		s.S3.Bucket = cfg.S3.Bucket
		s.S3.AccessKey = cfg.S3.AccessKey
		s.S3.Secret = cfg.S3.Secret
		s.S3.Region = cfg.S3.Region

		s.Batch.Enabled = true

		return app.Save(s)
	}, nil)

	migratecmd.MustRegister(app, app.RootCmd, migratecmd.Config{
		// enable auto creation of migration files when making collection changes in the Dashboard
		// (the IsProbablyGoRun check is to enable it only during development)
		Automigrate: osutils.IsProbablyGoRun(),
	})

	app.OnServe().BindFunc(func(se *core.ServeEvent) error {

		se.Router.GET("/ping", func(re *core.RequestEvent) error {
			return re.String(200, "pong")
		})

		routes.RegisterFriendRoutes(se)
		routes.RegisterCrewRoutes(se)
		routes.RegisterUsageRoutes(se)
		routes.RegisterGeoRoutes(se)
		routes.RegisterPartnerRoutes(se)
		routes.RegisterCouponRoutes(se)

		return se.Next()
	})

	if err := app.Start(); err != nil {
		log.Fatal(err)
	}
}
