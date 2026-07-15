package handler

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"bpa-calculator-go/internal/service"
)

type CalculatorHandler struct {
	calcService *service.CalculatorService
}

func NewCalculatorHandler(calcService *service.CalculatorService) *CalculatorHandler {
	return &CalculatorHandler{
		calcService: calcService,
	}
}

func (h *CalculatorHandler) Calculate(w http.ResponseWriter, r *http.Request) {
	var req service.CalculationReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		slog.Error("Failed to decode calculation request", "error", err)
		h.writeError(w, http.StatusBadRequest, "Invalid JSON body")
		return
	}

	res, err := h.calcService.Calculate(r.Context(), &req)
	if err != nil {
		slog.Error("Calculation failed", "error", err)
		h.writeError(w, http.StatusInternalServerError, "Calculation failed")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(res)
}

func (h *CalculatorHandler) GetBillAmount(w http.ResponseWriter, r *http.Request) {
	var req service.CalculationReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		slog.Error("Failed to decode get bill amount request", "error", err)
		h.writeError(w, http.StatusBadRequest, "Invalid JSON body")
		return
	}

	res, err := h.calcService.GetBillAmount(r.Context(), &req)
	if err != nil {
		slog.Error("GetBillAmount failed", "error", err)
		h.writeError(w, http.StatusInternalServerError, "Failed to get bill amount")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(res)
}

func (h *CalculatorHandler) writeError(w http.ResponseWriter, statusCode int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	json.NewEncoder(w).Encode(map[string]interface{}{
		"ResponseInfo": map[string]string{
			"status": "FAILED",
		},
		"Errors": []map[string]string{
			{
				"code":    "CALCULATOR_ERROR",
				"message": message,
			},
		},
	})
}
