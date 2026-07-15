package db

import (
	"context"
	"database/sql"
	"encoding/json"
	"log/slog"
	"time"

	_ "github.com/lib/pq"
)

type BPADbClient struct {
	db *sql.DB
}

func NewBPADbClient(dbUrl string) (*BPADbClient, error) {
	db, err := sql.Open("postgres", dbUrl)
	if err != nil {
		return nil, err
	}

	// Verify connection
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		return nil, err
	}

	// Connection pool config
	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(5 * time.Minute)

	slog.Info("Database connection established successfully")
	return &BPADbClient{db: db}, nil
}

func (c *BPADbClient) FetchBPA(ctx context.Context, applicationNo string) (map[string]interface{}, error) {
	var additionalDetails []byte
	err := c.db.QueryRowContext(ctx, "SELECT additionaldetails FROM eg_bpa_buildingplan WHERE applicationno = $1", applicationNo).Scan(&additionalDetails)
	if err != nil {
		slog.Error("Failed to query BPA from DB", "applicationNo", applicationNo, "error", err)
		return nil, err
	}
	
	var data map[string]interface{}
	if len(additionalDetails) > 0 {
		json.Unmarshal(additionalDetails, &data)
	}
	return data, nil
}
