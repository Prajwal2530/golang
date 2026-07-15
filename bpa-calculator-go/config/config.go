package config

import (
	"log/slog"
	"os"
	"regexp"
)

type Config struct {
	ServerPort      string
	DBUrl           string
	KafkaBrokers    string
	MDMSHost        string
	BillingHost     string
}

func LoadConfig() *Config {
	jdbcUrl := getEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://bpa-postgres:5432/bpa")
	dbUser := getEnv("SPRING_DATASOURCE_USERNAME", "postgres")
	dbPass := getEnv("SPRING_DATASOURCE_PASSWORD", "postgres")

	cfg := &Config{
		ServerPort:   getEnv("SERVER_PORT", "8080"),
		DBUrl:        parseJdbcToDsn(jdbcUrl, dbUser, dbPass),
		KafkaBrokers: getEnv("KAFKA_BROKER_ADDRESS", "bpa-kafka:9092"),
		MDMSHost:     getEnv("MDMS_HOST", "http://egov-mdms-service:8080"),
		BillingHost:  getEnv("BILLING_HOST", "http://billing-service:8080"),
	}
	
	slog.Info("Loaded Config", "port", cfg.ServerPort, "dbUser", dbUser)
	return cfg
}

func getEnv(key, fallback string) string {
	if value, exists := os.LookupEnv(key); exists {
		return value
	}
	return fallback
}

func parseJdbcToDsn(jdbcUrl, user, pass string) string {
	// Simple regex to extract host, port, db from jdbc:postgresql://host:port/db
	re := regexp.MustCompile(`jdbc:postgresql://([^:]+):(\d+)/(.+)`)
	matches := re.FindStringSubmatch(jdbcUrl)
	if len(matches) == 4 {
		return "postgres://" + user + ":" + pass + "@" + matches[1] + ":" + matches[2] + "/" + matches[3] + "?sslmode=disable"
	}
	return "postgres://" + user + ":" + pass + "@bpa-postgres:5432/bpa?sslmode=disable"
}
