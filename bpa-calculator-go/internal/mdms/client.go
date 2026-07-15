package mdms

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

type MDMSClient struct {
	host string
}

func NewMDMSClient(host string) *MDMSClient {
	return &MDMSClient{host: host}
}

func (c *MDMSClient) FetchFeeAmount(ctx context.Context, tenantId, feeType string) (float64, error) {
	// Fee configuration lives at state level — strip city suffix (e.g. "pb.amritsar" → "pb")
	stateTenantId := tenantId
	if idx := strings.Index(tenantId, "."); idx != -1 {
		stateTenantId = tenantId[:idx]
	}

	reqBody := map[string]interface{}{
		"RequestInfo": map[string]string{},
		"MdmsCriteria": map[string]interface{}{
			"tenantId": stateTenantId,
			"moduleDetails": []map[string]interface{}{
				{
					"moduleName": "BPA",
					"masterDetails": []map[string]interface{}{
						{"name": "CalculationType"},
					},
				},
			},
		},
	}
	
	bodyData, _ := json.Marshal(reqBody)
	resp, err := http.Post(c.host+"/egov-mdms-service/v1/_search", "application/json", bytes.NewReader(bodyData))
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()

	var result struct {
		MdmsRes struct {
			BPA struct {
				CalculationType []struct {
					FeeType string  `json:"feeType"`
					Amount  float64 `json:"amount"`
				} `json:"CalculationType"`
			} `json:"BPA"`
		} `json:"MdmsRes"`
	}

	bodyBytes, _ := io.ReadAll(resp.Body)
	if err := json.Unmarshal(bodyBytes, &result); err != nil {
		slog.Error("Failed to decode MDMS response", "error", err, "body", string(bodyBytes))
		return 0, err
	}

	for _, ct := range result.MdmsRes.BPA.CalculationType {
		if ct.FeeType == feeType {
			slog.Info("Found fee from MDMS", "feeType", feeType, "amount", ct.Amount)
			return ct.Amount, nil
		}
	}

	slog.Warn("Fee type not found in MDMS, defaulting to 0", "feeType", feeType)
	return 0, fmt.Errorf("feeType not found")
}
