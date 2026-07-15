package service

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"bpa-calculator-go/config"
	"bpa-calculator-go/internal/billing"
	"bpa-calculator-go/internal/db"
	"bpa-calculator-go/internal/kafka"
	"bpa-calculator-go/internal/mdms"
)

type RequestInfo struct {
	APIId         string `json:"apiId"`
	Ver           string `json:"ver"`
	Ts            int64  `json:"ts"`
	Action        string `json:"action"`
	DID           string `json:"did"`
	Key           string `json:"key"`
	MsgId         string `json:"msgId"`
	AuthToken     string `json:"authToken"`
	CorrelationId string `json:"correlationId"`
}

type ResponseInfo struct {
	APIId    string `json:"apiId"`
	Ver      string `json:"ver"`
	Ts       int64  `json:"ts"`
	ResMsgId string `json:"resMsgId"`
	MsgId    string `json:"msgId"`
	Status   string `json:"status"`
}

type CalculationReq struct {
	RequestInfo         RequestInfo           `json:"RequestInfo"`
	CalculationCriteria []CalculationCriteria `json:"CalculationCriteria"`
}

type CalculationCriteria struct {
	ApplicationNo string          `json:"applicationNo"`
	BPA           json.RawMessage `json:"bpa"`
	FeeType       string          `json:"feeType"`
	TenantId      string          `json:"tenantId"`
}

type CalculationRes struct {
	ResponseInfo ResponseInfo  `json:"ResponseInfo"`
	Calculations []Calculation `json:"Calculations"`
}

type Calculation struct {
	ApplicationNo    string            `json:"applicationNo"`
	TenantId         string            `json:"tenantId"`
	TaxHeadEstimates []TaxHeadEstimate `json:"taxHeadEstimates"`
	FeeType          string            `json:"feeType"`
}

type TaxHeadEstimate struct {
	TaxHeadCode    string  `json:"taxHeadCode"`
	EstimateAmount float64 `json:"estimateAmount"`
	Category       string  `json:"category"`
}

type CalculatorService struct {
	cfg      *config.Config
	mdmsCli  *mdms.MDMSClient
	billCli  *billing.BillingClient
	kafkaCli *kafka.Producer
	dbCli    *db.BPADbClient
}

func NewCalculatorService(cfg *config.Config, mdmsCli *mdms.MDMSClient, billCli *billing.BillingClient, kafkaCli *kafka.Producer, dbCli *db.BPADbClient) *CalculatorService {
	return &CalculatorService{
		cfg:      cfg,
		mdmsCli:  mdmsCli,
		billCli:  billCli,
		kafkaCli: kafkaCli,
		dbCli:    dbCli,
	}
}

func (s *CalculatorService) Calculate(ctx context.Context, req *CalculationReq) (*CalculationRes, error) {
	slog.Info("Processing calculate request", "applications", len(req.CalculationCriteria))

	calculations := make([]Calculation, 0)
	var demands []interface{}

	for _, criteria := range req.CalculationCriteria {
		// 1. Fetch from DB (can be used to extract plot area or other attributes later)
		bpaData, dbErr := s.dbCli.FetchBPA(ctx, criteria.ApplicationNo)
		if dbErr == nil {
			slog.Info("Fetched BPA from DB successfully", "appNo", criteria.ApplicationNo, "details", bpaData != nil)
		}

		// 2. Fetch Fee from MDMS based on feeType
		amount, err := s.mdmsCli.FetchFeeAmount(ctx, criteria.TenantId, criteria.FeeType)
		if err != nil {
			amount = 200.0 // Default fallback if not found in MDMS config
		}

		// 3. Prepare calculation payload
		calc := Calculation{
			ApplicationNo: criteria.ApplicationNo,
			TenantId:      criteria.TenantId,
			FeeType:       criteria.FeeType,
			TaxHeadEstimates: []TaxHeadEstimate{
				{
					TaxHeadCode:    criteria.FeeType,
					EstimateAmount: amount,
					Category:       "FEE",
				},
			},
		}
		calculations = append(calculations, calc)

		// Prepare demand
		demand := map[string]interface{}{
			"tenantId":      criteria.TenantId,
			"consumerCode":  criteria.ApplicationNo,
			"consumerType":  "BPA",
			"businessService": "BPA",
			"demandDetails": []map[string]interface{}{
				{
					"taxHeadMasterCode": criteria.FeeType,
					"taxAmount":         amount,
					"collectionAmount":  0,
				},
			},
		}
		demands = append(demands, demand)
	}

	// 4. Create Demand in billing service
	if len(demands) > 0 && len(req.CalculationCriteria) > 0 {
		err := s.billCli.CreateDemand(ctx, req.RequestInfo, req.CalculationCriteria[0].TenantId, demands)
		if err != nil {
			slog.Error("Failed to create demand", "error", err)
		}
	}

	res := &CalculationRes{
		ResponseInfo: buildResponseInfo(&req.RequestInfo, "SUCCESS"),
		Calculations: calculations,
	}

	// 5. Produce to Kafka (guard against empty slice)
	if len(res.Calculations) > 0 {
		_ = s.kafkaCli.Produce(ctx, "bpa-calculator-create-topic", res.Calculations[0].ApplicationNo, res)
	}

	return res, nil
}

func (s *CalculatorService) GetBillAmount(ctx context.Context, req *CalculationReq) (*CalculationRes, error) {
	slog.Info("Processing getBillamount request")
	return s.Calculate(ctx, req)
}

func buildResponseInfo(reqInfo *RequestInfo, status string) ResponseInfo {
	return ResponseInfo{
		APIId:    reqInfo.APIId,
		Ver:      reqInfo.Ver,
		Ts:       time.Now().UnixMilli(),
		ResMsgId: reqInfo.MsgId + "-res",
		MsgId:    reqInfo.MsgId,
		Status:   status,
	}
}
