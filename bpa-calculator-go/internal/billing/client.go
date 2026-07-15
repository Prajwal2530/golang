package billing

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
)

type BillingClient struct {
	host string
}

func NewBillingClient(host string) *BillingClient {
	return &BillingClient{host: host}
}

func (c *BillingClient) CreateDemand(ctx context.Context, reqInfo interface{}, tenantId string, demands []interface{}) error {
	reqBody := map[string]interface{}{
		"RequestInfo": reqInfo,
		"Demands":     demands,
	}

	bodyData, _ := json.Marshal(reqBody)
	slog.Info("Calling billing service to create demand")
	
	url := strings.TrimRight(c.host, "/") + "/billing-service/demand/_create"
	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewReader(bodyData))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Tenant-ID", tenantId)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		bodyBytes, _ := io.ReadAll(resp.Body)
		slog.Error("Billing service returned non-2xx status", "status", resp.StatusCode, "body", string(bodyBytes))
		return fmt.Errorf("billing service returned status %d", resp.StatusCode)
	}

	slog.Info("Demands created successfully")
	return nil
}
