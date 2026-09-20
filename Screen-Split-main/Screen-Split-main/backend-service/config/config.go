package config

import (
	"fmt"
	"os"

	"github.com/spf13/viper"
)

type Config struct {
	Meta struct {
		AppName       string `mapstructure:"appName"`
		AppURL        string `mapstructure:"appURL"`
		SenderName    string `mapstructure:"senderName"`
		SenderAddress string `mapstructure:"senderAddress"`
		HideControls  bool   `mapstructure:"hideControls"`
	} `mapstructure:"meta"`
	SMTP struct {
		Enabled  bool   `mapstructure:"enabled"`
		Host     string `mapstructure:"host"`
		Port     int    `mapstructure:"port"`
		Username string `mapstructure:"username"`
		Password string `mapstructure:"password"`
		TLS      bool   `mapstructure:"tls"`
	} `mapstructure:"smtp"`
	Logs struct {
		MaxDays int  `mapstructure:"maxDays"`
		LogIP   bool `mapstructure:"logIP"`
	} `mapstructure:"logs"`
	S3 struct {
		Enabled   bool   `mapstructure:"enabled"`
		Bucket    string `mapstructure:"bucket"`
		Region    string `mapstructure:"region"`
		Endpoint  string `mapstructure:"enpoint"`
		AccessKey string `mapstructure:"accessKey"`
		Secret    string `mapstructure:"secret"`
	} `mapstructure:"s3"`
}

func LoadSettings(configFile string) (*Config, error) {
	viper.SetConfigFile(configFile)
	viper.AutomaticEnv()

	if err := viper.ReadInConfig(); err != nil {
		return &Config{}, fmt.Errorf("failed to load the config %w", err)
	}

	for _, k := range viper.AllKeys() {
		v := viper.GetString(k)
		viper.Set(k, os.ExpandEnv(v))
	}

	var c Config
	if err := viper.Unmarshal(&c); err != nil {
		return &Config{}, fmt.Errorf("failed to unmarshel the config %w", err)
	}
	return &c, nil
}
