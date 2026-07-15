/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jayway.jsonpath.Configuration
 *  com.jayway.jsonpath.DocumentContext
 *  com.jayway.jsonpath.JsonPath
 *  com.jayway.jsonpath.Predicate
 *  com.jayway.jsonpath.TypeRef
 *  org.egov.common.contract.request.RequestInfo
 *  org.egov.tracer.model.CustomException
 *  org.egov.tracer.model.ServiceCallException
 *  org.json.JSONObject
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.BeanUtils
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.stereotype.Service
 *  org.springframework.util.CollectionUtils
 */
package org.egov.bpa.service;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Predicate;
import com.jayway.jsonpath.TypeRef;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.egov.bpa.config.BPAConfiguration;
import org.egov.bpa.repository.BPARepository;
import org.egov.bpa.repository.ServiceRequestRepository;
import org.egov.bpa.validator.MDMSValidator;
import org.egov.bpa.web.model.BPA;
import org.egov.bpa.web.model.BPARequest;
import org.egov.bpa.web.model.BPASearchCriteria;
import org.egov.bpa.web.model.edcr.RequestInfo;
import org.egov.bpa.web.model.edcr.RequestInfoWrapper;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ServiceCallException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class EDCRService {
    private static final Logger log = LoggerFactory.getLogger(EDCRService.class);
    private ServiceRequestRepository serviceRequestRepository;
    private BPAConfiguration config;
    @Autowired
    private MDMSValidator mdmsValidator;
    @Autowired
    BPARepository bpaRepository;

    @Autowired
    public EDCRService(ServiceRequestRepository serviceRequestRepository, BPAConfiguration config) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
    }

    public Map<String, String> validateEdcrPlan(BPARequest request, Object mdmsData) {
        LinkedList applicationType;
        List<BPA> bpas;
        String edcrNo = request.getBPA().getEdcrNumber();
        String riskType = request.getBPA().getRiskType();
        StringBuilder uri = new StringBuilder(this.config.getEdcrHost());
        BPA bpa = request.getBPA();
        BPASearchCriteria criteria = new BPASearchCriteria();
        criteria.setEdcrNumber(bpa.getEdcrNumber());
        Map<String, String> additionalDetails = bpa.getAdditionalDetails() != null ? (Map)bpa.getAdditionalDetails() : new HashMap();
        String bpaApplicationType = (String)additionalDetails.get("applicationType");
        if (bpaApplicationType.equals("BUILDING_OC_PLAN_SCRUTINY")) {
            criteria.setApplicationType("BUILDING_OC_PLAN_SCRUTINY");
        }
        if ((bpas = this.bpaRepository.getBPAData(criteria, null)).size() > 0) {
            for (int i = 0; i < bpas.size(); ++i) {
                if (bpas.get(i).getStatus().equalsIgnoreCase("REJECTED") || bpas.get(i).getStatus().equalsIgnoreCase("PERMIT REVOCATION")) continue;
                throw new CustomException("DUPLICATE EDCR", " Application already exists with EDCR Number " + bpa.getEdcrNumber());
            }
        }
        uri.append(this.config.getGetPlanEndPoint());
        uri.append("?").append("tenantId=").append(bpa.getTenantId());
        uri.append("&").append("edcrNumber=").append(edcrNo);
        RequestInfo edcrRequestInfo = new RequestInfo();
        BeanUtils.copyProperties((Object)request.getRequestInfo(), (Object)edcrRequestInfo);
        Map<String, List<String>> masterData = this.mdmsValidator.getAttributeValues(mdmsData);
        LinkedHashMap responseMap = null;
        try {
            responseMap = (LinkedHashMap)this.serviceRequestRepository.fetchResult(uri, new RequestInfoWrapper(edcrRequestInfo));
        }
        catch (ServiceCallException se) {
            throw new CustomException("EDCR ERROR", " EDCR Number is Invalid");
        }
        if (CollectionUtils.isEmpty((Map)responseMap)) {
            throw new CustomException("EDCR ERROR", "The response from EDCR service is empty or null");
        }
        String jsonString = new JSONObject((Map)responseMap).toString();
        DocumentContext context = JsonPath.using((Configuration)Configuration.defaultConfiguration()).parse(jsonString);
        List edcrStatus = (List)context.read("edcrDetail.*.status", new Predicate[0]);
        List OccupancyTypes = (List)context.read("edcrDetail.*.planDetail.virtualBuilding.occupancyTypes.*.type.code", new Predicate[0]);
        TypeRef<List<Double>> typeRef = new TypeRef<List<Double>>(){};
        LinkedList serviceType = (LinkedList)context.read("edcrDetail.*.applicationSubType", new Predicate[0]);
        if (serviceType != null && !serviceType.isEmpty() && additionalDetails.get("serviceType") != null && !((String)serviceType.get(0)).equalsIgnoreCase((String)additionalDetails.get("serviceType"))) {
            throw new CustomException("INVALID SERVICE TYPE", "The service type is invalid, it is not matching with scrutinized plan service type " + (String)serviceType.get(0));
        }
        if (serviceType == null || serviceType.size() == 0) {
            serviceType.add("NEW_CONSTRUCTION");
        }
        if (!((applicationType = (LinkedList)context.read("edcrDetail.*.appliactionType", new Predicate[0])) == null || applicationType.isEmpty() || additionalDetails.get("applicationType") == null || ((String)applicationType.get(0)).equalsIgnoreCase((String)additionalDetails.get("applicationType")) || bpaApplicationType.equals("BUILDING_OC_PLAN_SCRUTINY"))) {
            throw new CustomException("INVALID APPLICATION TYPE", "The application type is invalid, it is not matching with scrutinized plan application type " + (String)applicationType.get(0));
        }
        if (applicationType == null || applicationType.size() == 0) {
            applicationType.add("permit");
        }
        LinkedList permitNumber = (LinkedList)context.read("edcrDetail.*.permitNumber", new Predicate[0]);
        if (bpaApplicationType.equals("BUILDING_OC_PLAN_SCRUTINY")) {
            List far = (List)context.read("edcrDetail.*.planDetail.farDetails.providedFar", new Predicate[0]);
            List coverage = (List)context.read("edcrDetail.*.planDetail.coverage", new Predicate[0]);
            List buildingHeight = (List)context.read("edcrDetail.*.planDetail.blocks.*.building.buildingHeight", new Predicate[0]);
            List plotArea = (List)context.read("edcrDetail.*.planDetail.plot.plotBndryArea", new Predicate[0]);
            List totalBuitUpArea = (List)context.read("edcrDetail.*.planDetail.virtualBuilding.totalBuitUpArea", new Predicate[0]);
            List frontSetback = (List)context.read("$.edcrDetail[*].planDetail.blocks[*].setBacks[*].frontYard.mean", new Predicate[0]);
            List rearSetback = (List)context.read("$.edcrDetail[*].planDetail.blocks[*].setBacks[*].rearYard.mean", new Predicate[0]);
            List leftSetback = (List)context.read("$.edcrDetail[*].planDetail.blocks[*].setBacks[*].sideYard1.mean", new Predicate[0]);
            List rightSetback = (List)context.read("$.edcrDetail[*].planDetail.blocks[*].setBacks[*].sideYard2.mean", new Predicate[0]);
            List parkingProvided = (List)context.read("$.edcrDetail[*].planDetail.reportOutput.scrutinyDetails[?(@.key == 'Common_Parking')].detail[*].Provided", new Predicate[0]);
            HashMap<String, Integer> edcrDetails = new HashMap<String, Integer>();
            edcrDetails.put("far", (Integer)(far == null || far.size() == 0 ? Integer.valueOf(0) : (Serializable)far.get(0)));
            edcrDetails.put("coverage", (Integer)(coverage == null || coverage.size() == 0 ? Integer.valueOf(0) : (Serializable)coverage.get(0)));
            edcrDetails.put("buildingHeight", (Integer)(buildingHeight == null || buildingHeight.size() == 0 ? Integer.valueOf(0) : (Serializable)buildingHeight.get(0)));
            edcrDetails.put("plotArea", (Integer)(plotArea == null || plotArea.size() == 0 ? Integer.valueOf(0) : (Serializable)plotArea.get(0)));
            edcrDetails.put("totalBuitUpArea", (Integer)(totalBuitUpArea == null || totalBuitUpArea.size() == 0 ? Integer.valueOf(0) : (Serializable)totalBuitUpArea.get(0)));
            edcrDetails.put("parking", (Integer)(parkingProvided == null || parkingProvided.size() == 0 ? Integer.valueOf(0) : (Serializable)parkingProvided.get(0)));
            edcrDetails.put("frontSetback", (Integer)(frontSetback == null || frontSetback.size() == 0 ? Integer.valueOf(0) : (Serializable)frontSetback.get(0)));
            edcrDetails.put("rearSetback", (Integer)(rearSetback == null || rearSetback.size() == 0 ? Integer.valueOf(0) : (Serializable)rearSetback.get(0)));
            edcrDetails.put("leftSetback", (Integer)(leftSetback == null || leftSetback.size() == 0 ? Integer.valueOf(0) : (Serializable)leftSetback.get(0)));
            edcrDetails.put("rightSetback", (Integer)(rightSetback == null || rightSetback.size() == 0 ? Integer.valueOf(0) : (Serializable)rightSetback.get(0)));
            additionalDetails.put("edcrDetails", ((Object)edcrDetails).toString());
        }
        additionalDetails.put("serviceType", (String)serviceType.get(0));
        if (bpaApplicationType.equals("BUILDING_OC_PLAN_SCRUTINY")) {
            additionalDetails.put("applicationType", bpaApplicationType);
        } else {
            additionalDetails.put("applicationType", (String)applicationType.get(0));
        }
        List plotAreas = (List)context.read("edcrDetail.*.planDetail.plot.area", (TypeRef)typeRef);
        List buildingHeights = (List)context.read("edcrDetail.*.planDetail.blocks.*.building.buildingHeight", (TypeRef)typeRef);
        if (CollectionUtils.isEmpty((Collection)edcrStatus) || !((String)edcrStatus.get(0)).equalsIgnoreCase("Accepted")) {
            throw new CustomException("INVALID EDCR NUMBER", "The EDCR Number is not Accepted " + edcrNo);
        }
        this.validateOCEdcr(OccupancyTypes, plotAreas, buildingHeights, applicationType, masterData, riskType);
        return additionalDetails;
    }

    private void validateOCEdcr(List<String> OccupancyTypes, List<Double> plotAreas, List<Double> buildingHeights, LinkedList<String> applicationType, Map<String, List<String>> masterData, String riskType) {
        if (!(CollectionUtils.isEmpty(OccupancyTypes) || CollectionUtils.isEmpty(plotAreas) || CollectionUtils.isEmpty(buildingHeights) || applicationType.get(0).equalsIgnoreCase("BUILDING_OC_PLAN_SCRUTINY"))) {
            Double buildingHeight = Collections.max(buildingHeights);
            String OccupancyType = OccupancyTypes.get(0);
            Double plotArea = plotAreas.get(0);
            List jsonOutput = (List)JsonPath.read(masterData, (String)"RiskTypeComputation", (Predicate[])new Predicate[0]);
            log.info("jsonOutput: " + jsonOutput);
            String filterExp = "";
            List<String> riskTypes = new ArrayList<String>();
            if (plotArea > 1000.0 || buildingHeight >= 15.0) {
                riskTypes.add("HIGH");
            } else {
                filterExp = "$.[?((@.fromPlotArea < " + plotArea + " && @.toPlotArea >= " + plotArea + ") && ( @.fromBuildingHeight < " + buildingHeight + "  &&  @.toBuildingHeight >= " + buildingHeight + "  ))].riskType";
                log.info("filterExp: " + filterExp);
                riskTypes = (List)JsonPath.read((Object)jsonOutput, (String)filterExp, (Predicate[])new Predicate[0]);
            }
            log.info("riskTypes: " + riskTypes);
            if (!CollectionUtils.isEmpty(riskTypes)) {
                String expectedRiskType = (String)riskTypes.get(0);
                log.info("expectedRiskType: " + expectedRiskType);
                if (expectedRiskType == null || !expectedRiskType.equals(riskType)) {
                    throw new CustomException("INVALID RISK TYPE", "The Risk Type is not valid " + riskType);
                }
            } else {
                throw new CustomException("INVALID OCCUPANCY TYPE", "The OccupancyType " + OccupancyType + " is not supported! ");
            }
        }
    }

    public String getEDCRPdfUrl(BPARequest bpaRequest) {
        BPA bpa = bpaRequest.getBPA();
        StringBuilder uri = new StringBuilder(this.config.getEdcrHost());
        uri.append(this.config.getGetPlanEndPoint());
        uri.append("?").append("tenantId=").append(bpa.getTenantId());
        uri.append("&").append("edcrNumber=").append(bpaRequest.getBPA().getEdcrNumber());
        RequestInfo edcrRequestInfo = new RequestInfo();
        BeanUtils.copyProperties((Object)bpaRequest.getRequestInfo(), (Object)edcrRequestInfo);
        LinkedHashMap responseMap = null;
        try {
            responseMap = (LinkedHashMap)this.serviceRequestRepository.fetchResult(uri, new RequestInfoWrapper(edcrRequestInfo));
        }
        catch (ServiceCallException se) {
            throw new CustomException("EDCR ERROR", " EDCR Number is Invalid");
        }
        String jsonString = new JSONObject((Map)responseMap).toString();
        DocumentContext context = JsonPath.using((Configuration)Configuration.defaultConfiguration()).parse(jsonString);
        List planReports = (List)context.read("edcrDetail.*.planReport", new Predicate[0]);
        return CollectionUtils.isEmpty((Collection)planReports) ? null : (String)planReports.get(0);
    }

    public Map<String, String> getEDCRDetails(org.egov.common.contract.request.RequestInfo requestInfo, BPA bpa) {
        List applicationType;
        String edcrNo = bpa.getEdcrNumber();
        StringBuilder uri = new StringBuilder(this.config.getEdcrHost());
        uri.append(this.config.getGetPlanEndPoint());
        uri.append("?").append("tenantId=").append(bpa.getTenantId());
        uri.append("&").append("edcrNumber=").append(edcrNo);
        RequestInfo edcrRequestInfo = new RequestInfo();
        BeanUtils.copyProperties((Object)requestInfo, (Object)edcrRequestInfo);
        LinkedHashMap responseMap = null;
        try {
            responseMap = (LinkedHashMap)this.serviceRequestRepository.fetchResult(uri, new RequestInfoWrapper(edcrRequestInfo));
        }
        catch (ServiceCallException se) {
            throw new CustomException("EDCR ERROR", " EDCR Number is Invalid");
        }
        if (CollectionUtils.isEmpty((Map)responseMap)) {
            throw new CustomException("EDCR ERROR", "The response from EDCR service is empty or null");
        }
        String jsonString = new JSONObject((Map)responseMap).toString();
        DocumentContext context = JsonPath.using((Configuration)Configuration.defaultConfiguration()).parse(jsonString);
        HashMap<String, String> edcrDetails = new HashMap<String, String>();
        List serviceType = (List)context.read("edcrDetail.*.planDetail.planInformation.serviceType", new Predicate[0]);
        List occupancy = (List)context.read("edcrDetail.*.planDetail.planInformation.occupancy", new Predicate[0]);
        if (CollectionUtils.isEmpty((Collection)serviceType)) {
            serviceType.add("NEW_CONSTRUCTION");
        }
        if (CollectionUtils.isEmpty((Collection)(applicationType = (List)context.read("edcrDetail.*.appliactionType", new Predicate[0])))) {
            applicationType.add("permit");
        }
        List approvalNo = (List)context.read("edcrDetail.*.permitNumber", new Predicate[0]);
        edcrDetails.put("serviceType", ((String)serviceType.get(0)).toString());
        edcrDetails.put("Occupancy", ((String)occupancy.get(0)).toString());
        edcrDetails.put("applicationType", ((String)applicationType.get(0)).toString());
        if (approvalNo.size() > 0 && approvalNo != null) {
            edcrDetails.put("permitNumber", ((String)approvalNo.get(0)).toString());
        }
        return edcrDetails;
    }

    public List<String> getEDCRNos(BPASearchCriteria searchCriteria, org.egov.common.contract.request.RequestInfo requestInfo) {
        StringBuilder uri = new StringBuilder(this.config.getEdcrHost());
        uri.append(this.config.getGetPlanEndPoint());
        uri.append("?").append("tenantId=").append(searchCriteria.getTenantId());
        RequestInfo edcrRequestInfo = new RequestInfo();
        BeanUtils.copyProperties((Object)requestInfo, (Object)edcrRequestInfo);
        LinkedHashMap responseMap = null;
        try {
            responseMap = (LinkedHashMap)this.serviceRequestRepository.fetchResult(uri, new RequestInfoWrapper(edcrRequestInfo));
        }
        catch (ServiceCallException se) {
            throw new CustomException("EDCR ERROR", " Invalid search criteria");
        }
        String jsonString = new JSONObject((Map)responseMap).toString();
        DocumentContext context = JsonPath.using((Configuration)Configuration.defaultConfiguration()).parse(jsonString);
        List edcrNos = (List)context.read("edcrDetail.*.edcrNumber", new Predicate[0]);
        return CollectionUtils.isEmpty((Collection)edcrNos) ? null : edcrNos;
    }
}
