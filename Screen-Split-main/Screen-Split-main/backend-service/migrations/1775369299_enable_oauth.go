package migrations

import (
	"os"

	"github.com/pocketbase/pocketbase/core"
	m "github.com/pocketbase/pocketbase/migrations"
	"github.com/pocketbase/pocketbase/tools/auth"
)

func init() {
	m.Register(func(app core.App) error {
		users, err := app.FindCollectionByNameOrId("users")
		if err != nil {
			return err
		}
		
		// get Google provider instance with some defaults.
		googleProvider, err := auth.NewProviderByName(auth.NameGoogle)
		if err != nil {
			return err
		}
		
		pkce := googleProvider.PKCE()
		users.OAuth2.Enabled = true
		users.OAuth2.Providers = []core.OAuth2ProviderConfig{
			{
				Name: auth.NameGoogle,
				DisplayName: googleProvider.DisplayName(),
				PKCE: &pkce,
				AuthURL: googleProvider.AuthURL(),
				TokenURL: googleProvider.TokenURL(),
				UserInfoURL: googleProvider.UserInfoURL(),
				ClientId: os.Getenv("GOOGLE_OAUTH_CLIENT_ID"),
				ClientSecret: os.Getenv("GOOGLE_OAUTH_CLIENT_SECRET"),
			},
		}

		return app.Save(users)
	}, func(app core.App) error {
		users, err := app.FindCollectionByNameOrId("users")
		if err != nil {
			return err
		}
		
		users.OAuth2.Enabled = true
		users.OAuth2.Providers = []core.OAuth2ProviderConfig{}
		
		return app.Save(users)
	})
}
