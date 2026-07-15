package main

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"bpa-calculator-go/config"
	"bpa-calculator-go/internal/billing"
	"bpa-calculator-go/internal/db"
	"bpa-calculator-go/internal/handler"
	"bpa-calculator-go/internal/kafka"
	"bpa-calculator-go/internal/mdms"
	"bpa-calculator-go/internal/service"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	slog.SetDefault(logger)

	cfg := config.LoadConfig()

	dbCli, err := db.NewBPADbClient(cfg.DBUrl)
	if err != nil {
		slog.Error("Failed to initialize DB client", "error", err)
		os.Exit(1)
	}

	mdmsCli := mdms.NewMDMSClient(cfg.MDMSHost)
	billCli := billing.NewBillingClient(cfg.BillingHost)
	kafkaCli := kafka.NewProducer(cfg.KafkaBrokers)
	defer kafkaCli.Close()

	calcService := service.NewCalculatorService(cfg, mdmsCli, billCli, kafkaCli, dbCli)
	calcHandler := handler.NewCalculatorHandler(calcService)

	mux := http.NewServeMux()
	
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "UP"})
	})

	mux.HandleFunc("POST /bpa-calculator/v1/_calculate", calcHandler.Calculate)
	mux.HandleFunc("POST /bpa-calculator/_calculate", calcHandler.Calculate)
	mux.HandleFunc("POST /bpa-calculator/v1/_getBillamount", calcHandler.GetBillAmount)
	mux.HandleFunc("POST /bpa-calculator/_getBillamount", calcHandler.GetBillAmount)

	server := &http.Server{
		Addr:    ":" + cfg.ServerPort,
		Handler: mux,
	}

	go func() {
		slog.Info("Starting server on port", "port", cfg.ServerPort)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("Server failed", "error", err)
			os.Exit(1)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop

	slog.Info("Shutting down server...")
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		slog.Error("Server forced to shutdown", "error", err)
	}

	slog.Info("Server exited")
}
