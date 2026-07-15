/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jayway.jsonpath.JsonPath
 *  com.jayway.jsonpath.PathNotFoundException
 *  com.jayway.jsonpath.Predicate
 *  org.apache.commons.lang.StringUtils
 *  org.egov.bpa.config.BPAConfiguration
 *  org.egov.bpa.service.NocService
 *  org.egov.bpa.util.BPAUtil
 *  org.egov.bpa.validator.MDMSValidator
 *  org.egov.bpa.web.model.BPA
 *  org.egov.bpa.web.model.BPARequest
 *  org.egov.bpa.web.model.BPASearchCriteria
 *  org.egov.bpa.web.model.NOC.Noc
 *  org.egov.common.contract.request.RequestInfo
 *  org.egov.tracer.model.CustomException
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.stereotype.Component
 *  org.springframework.util.CollectionUtils
 */
package org.egov.bpa.validator;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.Predicate;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.egov.bpa.config.BPAConfiguration;
import org.egov.bpa.service.EDCRService;
import org.egov.bpa.service.NocService;
import org.egov.bpa.util.BPAUtil;
import org.egov.bpa.validator.MDMSValidator;
import org.egov.bpa.web.model.BPA;
import org.egov.bpa.web.model.BPARequest;
import org.egov.bpa.web.model.BPASearchCriteria;
import org.egov.bpa.web.model.NOC.Noc;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class BPAValidator {
    private static final Logger log = LoggerFactory.getLogger(BPAValidator.class);
    @Autowired
    private MDMSValidator mdmsValidator;
    @Autowired
    private BPAConfiguration config;
    @Autowired
    private EDCRService edcrService;
    @Autowired
    private BPAUtil bpaUtil;
    @Autowired
    private NocService nocService;

    public void validateCreate(BPARequest bpaRequest, Object mdmsData, Map<String, String> values) {
        this.mdmsValidator.validateMdmsData(bpaRequest, mdmsData);
        this.validateApplicationDocuments(bpaRequest, mdmsData, null, values);
    }

    private void validateApplicationDocuments(BPARequest request, Object mdmsData, String currentState, Map<String, String> values) {
        Map masterData = this.mdmsValidator.getAttributeValues(mdmsData);
        BPA bpa = request.getBPA();
        if (!(bpa.getWorkflow().getAction().equalsIgnoreCase("REJECT") || bpa.getWorkflow().getAction().equalsIgnoreCase("ADHOC") || bpa.getWorkflow().getAction().equalsIgnoreCase("PAY"))) {
            String applicationType = values.get("applicationType");
            String serviceType = values.get("serviceType");
            String filterExp = "$.[?(@.applicationType=='" + applicationType + "' && @.ServiceType=='" + serviceType + "' && @.RiskType=='" + bpa.getRiskType() + "' && @.WFState=='" + currentState + "')].docTypes";
            List docTypeMappings = (List)JsonPath.read(masterData.get("DocTypeMapping"), (String)filterExp, (Predicate[])new Predicate[0]);
            ArrayList allDocuments = new ArrayList();
            if (bpa.getDocuments() != null) {
                allDocuments.addAll(bpa.getDocuments());
            }
            if (CollectionUtils.isEmpty((Collection)docTypeMappings)) {
                return;
            }
            filterExp = "$.[?(@.required==true)].code";
            List requiredDocTypes = (List)JsonPath.read(docTypeMappings.get(0), (String)filterExp, (Predicate[])new Predicate[0]);
            List validDocumentTypes = (List)masterData.get("DocumentType");
            if (!CollectionUtils.isEmpty(allDocuments)) {
                allDocuments.forEach(document -> {
                    if (!validDocumentTypes.contains(document.getDocumentType())) {
                        throw new CustomException("BPA UNKNOWN DOCUMENTTYPE", document.getDocumentType() + " is Unkown");
                    }
                });
                if (requiredDocTypes.size() > 0 && allDocuments.size() < requiredDocTypes.size()) {
                    throw new CustomException("BPA MDNADATORY DOCUMENTPYE MISSING", "Please upload required Documents");
                }
                if (requiredDocTypes.size() > 0) {
                    ArrayList addedDocTypes = new ArrayList();
                    allDocuments.forEach(document -> {
                        String docType = document.getDocumentType();
                        int lastIndex = docType.lastIndexOf(".");
                        String documentNs = "";
                        if (lastIndex > 1) {
                            documentNs = docType.substring(0, lastIndex);
                        } else {
                            if (lastIndex == 1) {
                                throw new CustomException("BPA INVALID DOCUMENTTYPE", document.getDocumentType() + " is Invalid");
                            }
                            documentNs = docType;
                        }
                        addedDocTypes.add(documentNs);
                    });
                    requiredDocTypes.forEach(docType -> {
                        String docType1 = docType.toString();
                        if (!addedDocTypes.contains(docType1)) {
                            throw new CustomException("BPA MDNADATORY DOCUMENTPYE MISSING", "Document Type " + docType1 + " is Missing");
                        }
                    });
                }
            } else if (requiredDocTypes.size() > 0) {
                throw new CustomException("BPA MDNADATORY DOCUMENTPYE MISSING", "Atleast " + requiredDocTypes.size() + " Documents are requied ");
            }
            bpa.setDocuments(allDocuments);
        }
    }

    private void validateDuplicateDocuments(BPARequest request) {
        if (request.getBPA().getDocuments() != null) {
            LinkedList documentFileStoreIds = new LinkedList();
            request.getBPA().getDocuments().forEach(document -> {
                if (documentFileStoreIds.contains(document.getFileStoreId())) {
                    throw new CustomException("BPA DUPLICATE DOCUMENT", "Same document cannot be used multiple times");
                }
                documentFileStoreIds.add(document.getFileStoreId());
            });
        }
    }

    public void validateSearch(RequestInfo requestInfo, BPASearchCriteria criteria) {
        if (!requestInfo.getUserInfo().getType().equalsIgnoreCase("CITIZEN") && criteria.isEmpty()) {
            throw new CustomException("INVALID SEARCH", "Search without any paramters is not allowed");
        }
        if (!requestInfo.getUserInfo().getType().equalsIgnoreCase("CITIZEN") && !criteria.tenantIdOnly() && criteria.getTenantId() == null) {
            throw new CustomException("INVALID SEARCH", "TenantId is mandatory in search");
        }
        if (requestInfo.getUserInfo().getType().equalsIgnoreCase("CITIZEN") && !criteria.isEmpty() && !criteria.tenantIdOnly() && criteria.getTenantId() == null) {
            throw new CustomException("INVALID SEARCH", "TenantId is mandatory in search");
        }
        String allowedParamStr = null;
        if (requestInfo.getUserInfo().getType().equalsIgnoreCase("CITIZEN")) {
            allowedParamStr = this.config.getAllowedCitizenSearchParameters();
        } else if (requestInfo.getUserInfo().getType().equalsIgnoreCase("EMPLOYEE")) {
            allowedParamStr = this.config.getAllowedEmployeeSearchParameters();
        } else {
            throw new CustomException("INVALID SEARCH", "The userType: " + requestInfo.getUserInfo().getType() + " does not have any search config");
        }
        if (StringUtils.isEmpty((String)allowedParamStr) && !criteria.isEmpty()) {
            throw new CustomException("INVALID SEARCH", "No search parameters are expected");
        }
        List<String> allowedParams = Arrays.asList(allowedParamStr.split(","));
        this.validateSearchParams(criteria, allowedParams);
    }

    private void validateSearchParams(BPASearchCriteria criteria, List<String> allowedParams) {
        if (criteria.getApplicationNo() != null && !allowedParams.contains("applicationNo")) {
            throw new CustomException("INVALID SEARCH", "Search on applicationNo is not allowed");
        }
        if (criteria.getEdcrNumber() != null && !allowedParams.contains("edcrNumber")) {
            throw new CustomException("INVALID SEARCH", "Search on edcrNumber is not allowed");
        }
        if (criteria.getStatus() != null && !allowedParams.contains("status")) {
            throw new CustomException("INVALID SEARCH", "Search on Status is not allowed");
        }
        if (criteria.getIds() != null && !allowedParams.contains("ids")) {
            throw new CustomException("INVALID SEARCH", "Search on ids is not allowed");
        }
        if (criteria.getMobileNumber() != null && !allowedParams.contains("mobileNumber")) {
            throw new CustomException("INVALID SEARCH", "Search on mobileNumber is not allowed");
        }
        if (criteria.getOffset() != null && !allowedParams.contains("offset")) {
            throw new CustomException("INVALID SEARCH", "Search on offset is not allowed");
        }
        if (criteria.getLimit() != null && !allowedParams.contains("limit")) {
            throw new CustomException("INVALID SEARCH", "Search on limit is not allowed");
        }
        if (criteria.getApprovalDate() != null && criteria.getApprovalDate() > new Date().getTime()) {
            throw new CustomException("INVALID SEARCH", "Permit Order Genarated date cannot be a future date");
        }
        if (criteria.getFromDate() != null && criteria.getFromDate() > new Date().getTime()) {
            throw new CustomException("INVALID SEARCH", "From date cannot be a future date");
        }
        if (criteria.getToDate() != null && criteria.getFromDate() != null && criteria.getFromDate() > criteria.getToDate()) {
            throw new CustomException("INVALID SEARCH", "To date cannot be prior to from date");
        }
    }

    public void validateUpdate(BPARequest bpaRequest, List<BPA> searchResult, Object mdmsData, String currentState, Map<String, String> edcrResponse) {
        BPA bpa = bpaRequest.getBPA();
        this.validateApplicationDocuments(bpaRequest, mdmsData, currentState, edcrResponse);
        this.validateAllIds(searchResult, bpa);
        this.mdmsValidator.validateMdmsData(bpaRequest, mdmsData);
        this.validateDuplicateDocuments(bpaRequest);
        this.setFieldsFromSearch(bpaRequest, searchResult, mdmsData);
    }

    private void setFieldsFromSearch(BPARequest bpaRequest, List<BPA> searchResult, Object mdmsData) {
        HashMap idToBPAFromSearch = new HashMap();
        searchResult.forEach(bpa -> idToBPAFromSearch.put(bpa.getId(), bpa));
        bpaRequest.getBPA().getAuditDetails().setCreatedBy(((BPA)idToBPAFromSearch.get(bpaRequest.getBPA().getId())).getAuditDetails().getCreatedBy());
        bpaRequest.getBPA().getAuditDetails().setCreatedTime(((BPA)idToBPAFromSearch.get(bpaRequest.getBPA().getId())).getAuditDetails().getCreatedTime());
        bpaRequest.getBPA().setStatus(((BPA)idToBPAFromSearch.get(bpaRequest.getBPA().getId())).getStatus());
    }

    private void validateAllIds(List<BPA> searchResult, BPA bpa) {
        HashMap idToBPAFromSearch = new HashMap();
        searchResult.forEach(bpas -> idToBPAFromSearch.put(bpas.getId(), bpas));
        HashMap<String, String> errorMap = new HashMap<String, String>();
        BPA searchedBpa = (BPA)idToBPAFromSearch.get(bpa.getId());
        if (!searchedBpa.getApplicationNo().equalsIgnoreCase(bpa.getApplicationNo())) {
            errorMap.put("INVALID UPDATE", "The application number from search: " + searchedBpa.getApplicationNo() + " and from update: " + bpa.getApplicationNo() + " does not match");
        }
        if (!searchedBpa.getId().equalsIgnoreCase(bpa.getId())) {
            errorMap.put("INVALID UPDATE", "The id " + bpa.getId() + " does not exist");
        }
        if (!CollectionUtils.isEmpty(errorMap)) {
            throw new CustomException(errorMap);
        }
    }

    public void validateCheckList(Object mdmsData, BPARequest bpaRequest, String wfState) {
        BPA bpa = bpaRequest.getBPA();
        Map<String, String> edcrResponse = this.edcrService.getEDCRDetails(bpaRequest.getRequestInfo(), bpaRequest.getBPA());
        log.debug("applicationType is " + edcrResponse.get("applicationType"));
        log.debug("serviceType is " + edcrResponse.get("serviceType"));
        this.validateQuestions(mdmsData, bpa, wfState, edcrResponse);
        this.validateFIDocTypes(mdmsData, bpa, wfState, edcrResponse);
    }

    private void validateQuestions(Object mdmsData, BPA bpa, String wfState, Map<String, String> edcrResponse) {
        block13: {
            List mdmsQns = null;
            log.debug("Fetching MDMS result for the state " + wfState);
            try {
                String questionsPath = "$.MdmsRes.BPA.CheckList[?(@.WFState==\"{1}\" && @.RiskType==\"{2}\" && @.ServiceType==\"{3}\" && @.applicationType==\"{4}\")].questions".replace("{1}", wfState).replace("{2}", bpa.getRiskType().toString()).replace("{3}", edcrResponse.get("serviceType")).replace("{4}", edcrResponse.get("applicationType"));
                List mdmsQuestionsArray = (List)JsonPath.read((Object)mdmsData, (String)questionsPath, (Predicate[])new Predicate[0]);
                if (!CollectionUtils.isEmpty((Collection)mdmsQuestionsArray)) {
                    mdmsQns = (List)JsonPath.read(mdmsQuestionsArray.get(0), (String)"$.[?(@.active==true)].question", (Predicate[])new Predicate[0]);
                }
                log.debug("MDMS questions " + mdmsQns);
                if (CollectionUtils.isEmpty((Collection)mdmsQns)) break block13;
                if (bpa.getAdditionalDetails() != null) {
                    List checkListFromReq = (List)((Map)bpa.getAdditionalDetails()).get(wfState.toLowerCase());
                    if (!CollectionUtils.isEmpty((Collection)checkListFromReq)) {
                        for (int i = 0; i < checkListFromReq.size(); ++i) {
                            List questions;
                            if (((Map)checkListFromReq.get(i)).containsKey("isDeleted")) {
                                checkListFromReq.remove(i);
                                --i;
                                continue;
                            }
                            ArrayList requestCheckList = new ArrayList();
                            ArrayList<String> requestQns = new ArrayList<String>();
                            this.validateDateTime((Map)checkListFromReq.get(i));
                            List list = questions = ((Map)checkListFromReq.get(i)).get("questions") != null ? (List)((Map)checkListFromReq.get(i)).get("questions") : null;
                            if (questions != null) {
                                requestCheckList.addAll(questions);
                            }
                            if (!CollectionUtils.isEmpty(requestCheckList)) {
                                for (Map reqQn : requestCheckList) {
                                    requestQns.add((String)reqQn.get("question"));
                                }
                            }
                            log.debug("Request questions " + requestQns);
                            if (!CollectionUtils.isEmpty(requestQns)) {
                                if (requestQns.size() < mdmsQns.size()) {
                                    throw new CustomException("BPA UNKNOWN QUESTIONS", "Please answer the required questions");
                                }
                                ArrayList<String> pendingQns = new ArrayList<String>();
                                for (String qn : mdmsQns) {
                                    if (requestQns.contains(qn)) continue;
                                    pendingQns.add(qn);
                                }
                                if (pendingQns.size() <= 0) continue;
                                throw new CustomException("BPA UNKNOWN QUESTIONS", "Please answer the required questions");
                            }
                            throw new CustomException("BPA UNKNOWN QUESTIONS", "Please answer the required questions");
                        }
                        break block13;
                    }
                    throw new CustomException("BPA UNKNOWN QUESTIONS", "Please answer the required questions");
                }
                throw new CustomException("BPA UNKNOWN QUESTIONS", "Please answer the required questions");
            }
            catch (PathNotFoundException ex) {
                log.error("Exception occured while validating the Checklist Questions" + ex.getMessage());
            }
        }
    }

    private void validateFIDocTypes(Object mdmsData, BPA bpa, String wfState, Map<String, String> edcrResponse) {
        block16: {
            List mdmsDocs = null;
            log.debug("Fetching MDMS result for the state " + wfState);
            try {
                String docTypesPath = "$.MdmsRes.BPA.CheckList[?(@.WFState==\"{1}\" && @.RiskType==\"{2}\" && @.ServiceType==\"{3}\" && @.applicationType==\"{4}\")].docTypes".replace("{1}", wfState).replace("{2}", bpa.getRiskType().toString()).replace("{3}", edcrResponse.get("serviceType")).replace("{4}", edcrResponse.get("applicationType"));
                List docTypesArray = (List)JsonPath.read((Object)mdmsData, (String)docTypesPath, (Predicate[])new Predicate[0]);
                if (!CollectionUtils.isEmpty((Collection)docTypesArray)) {
                    mdmsDocs = (List)JsonPath.read(docTypesArray.get(0), (String)"$.[?(@.required==true)].code", (Predicate[])new Predicate[0]);
                }
                log.debug("MDMS DocTypes " + mdmsDocs);
                if (CollectionUtils.isEmpty((Collection)mdmsDocs)) break block16;
                if (bpa.getAdditionalDetails() != null) {
                    List checkListFromReq = (List)((Map)bpa.getAdditionalDetails()).get(wfState.toLowerCase());
                    if (!CollectionUtils.isEmpty((Collection)checkListFromReq)) {
                        for (int i = 0; i < checkListFromReq.size(); ++i) {
                            List docs;
                            ArrayList requestCheckList = new ArrayList();
                            ArrayList<String> requestDocs = new ArrayList<String>();
                            List list = docs = ((Map)checkListFromReq.get(i)).get("docs") != null ? (List)((Map)checkListFromReq.get(i)).get("docs") : null;
                            if (docs != null) {
                                requestCheckList.addAll(docs);
                            }
                            if (!CollectionUtils.isEmpty(requestCheckList)) {
                                for (Map reqDoc : requestCheckList) {
                                    String fileStoreId = (String)reqDoc.get("fileStoreId");
                                    if (!StringUtils.isEmpty((String)fileStoreId)) {
                                        String docType = (String)reqDoc.get("documentType");
                                        int lastIndex = docType.lastIndexOf(".");
                                        String documentNs = "";
                                        if (lastIndex > 1) {
                                            documentNs = docType.substring(0, lastIndex);
                                        } else {
                                            if (lastIndex == 1) {
                                                throw new CustomException("BPA INVALID DOCUMENTTYPE", (String)reqDoc.get("documentType") + " is Invalid");
                                            }
                                            documentNs = docType;
                                        }
                                        requestDocs.add(documentNs);
                                        continue;
                                    }
                                    throw new CustomException("BPA UNKNOWN DOCS", "Please upload required Documents");
                                }
                            }
                            log.debug("Request Docs " + requestDocs);
                            if (!CollectionUtils.isEmpty(requestDocs)) {
                                if (requestDocs.size() < mdmsDocs.size()) {
                                    throw new CustomException("BPA UNKNOWN DOCS", "Please upload required Documents");
                                }
                                ArrayList<String> pendingDocs = new ArrayList<String>();
                                for (String doc : mdmsDocs) {
                                    if (requestDocs.contains(doc)) continue;
                                    pendingDocs.add(doc);
                                }
                                if (pendingDocs.size() <= 0) continue;
                                throw new CustomException("BPA UNKNOWN DOCS", "Please upload required Documents");
                            }
                            throw new CustomException("BPA UNKNOWN DOCS", "Please upload required Documents");
                        }
                        break block16;
                    }
                    throw new CustomException("BPA UNKNOWN DOCS", "Please upload required Documents");
                }
                throw new CustomException("BPA UNKNOWN DOCS", "Please upload required Documents");
            }
            catch (PathNotFoundException ex) {
                log.error("Exception occured while validating the Checklist Documents" + ex.getMessage());
            }
        }
    }

    private void validateDateTime(Map checkListFromRequest) {
        if (checkListFromRequest.get("date") == null || StringUtils.isEmpty((String)checkListFromRequest.get("date").toString())) {
            throw new CustomException("BPA UNKNOWN DATE", "Please mention the inspection date");
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date dt = sdf.parse(checkListFromRequest.get("date").toString());
            long inspectionEpoch = dt.getTime();
            if (inspectionEpoch > new Date().getTime()) {
                throw new CustomException("BPA UNKNOWN DATE", "Inspection date cannot be a future date");
            }
            if (inspectionEpoch < 0L) {
                throw new CustomException("BPA UNKNOWN DATE", "Provide the date in specified format 'yyyy-MM-dd'");
            }
        }
        catch (ParseException e) {
            throw new CustomException("BPA UNKNOWN DATE", "Unable to parase the inspection date");
        }
        if (checkListFromRequest.get("time") == null || StringUtils.isEmpty((String)checkListFromRequest.get("time").toString())) {
            throw new CustomException("BPA UNKNOWN TIME", "Please mention the inspection time");
        }
    }

    public void validatePreEnrichData(BPARequest bpaRequest, Object mdmsRes) {
        this.validateSkipPaymentAction(bpaRequest);
        this.validateNocApprove(bpaRequest, mdmsRes);
    }

    private void validateSkipPaymentAction(BPARequest bpaRequest) {
        BigDecimal demandAmount;
        BPA bpa = bpaRequest.getBPA();
        if (bpa.getWorkflow().getAction() != null && bpa.getWorkflow().getAction().equalsIgnoreCase("SKIP_PAYMENT") && (demandAmount = this.bpaUtil.getDemandAmount(bpaRequest)).compareTo(BigDecimal.ZERO) > 0) {
            throw new CustomException("BPA INVALID ACTION", "Payment can't be skipped once demand is generated.");
        }
    }

    private void validateNocApprove(BPARequest bpaRequest, Object mdmsRes) {
        BPA bpa = bpaRequest.getBPA();
        log.debug("===========> valdiateNocApprove method called");
        if (this.config.getValidateRequiredNoc().booleanValue() && bpa.getStatus().equalsIgnoreCase("NOC_VERIFICATION_INPROGRESS") && bpa.getWorkflow().getAction().equalsIgnoreCase("FORWARD")) {
            Map<String, String> edcrResponse = this.edcrService.getEDCRDetails(bpaRequest.getRequestInfo(), bpaRequest.getBPA());
            log.debug("===========> valdiateNocApprove method called, application is in noc verification pending");
            String riskType = "ALL";
            if (StringUtils.isEmpty((String)bpa.getRiskType()) || bpa.getRiskType().equalsIgnoreCase("LOW")) {
                riskType = bpa.getRiskType();
            }
            log.debug("fetching NocTypeMapping record having riskType : " + riskType);
            String nocPath = "$.MdmsRes.BPA.NocTypeMapping[?(@.applicationType==\"{1}\" && @.serviceType==\"{2}\" && @.riskType==\"{3}\")].nocTypes".replace("{1}", edcrResponse.get("applicationType")).replace("{2}", edcrResponse.get("serviceType")).replace("{3}", riskType);
            List nocMappingResponse = (List)JsonPath.read((Object)mdmsRes, (String)nocPath, (Predicate[])new Predicate[0]);
            List nocTypes = (List)JsonPath.read((Object)nocMappingResponse, (String)"$..type", (Predicate[])new Predicate[0]);
            log.debug("===========> valdiateNocApprove method called, noctypes====", (Object)nocTypes);
            List nocs = this.nocService.fetchNocRecords(bpaRequest);
            if (!CollectionUtils.isEmpty((Collection)nocs)) {
                for (Noc noc : nocs) {
                    List<String> statuses;
                    if (nocTypes.isEmpty() || !nocTypes.contains(noc.getNocType()) || (statuses = Arrays.asList(this.config.getNocValidationCheckStatuses().split(","))).contains(noc.getApplicationStatus())) continue;
                    log.error("Noc is not approved having applicationNo :" + noc.getApplicationNo());
                    throw new CustomException("NOC_SERVICE_EXCEPTION", " Application can't be forwarded without NOC " + StringUtils.join(statuses, (String)" or "));
                }
            } else {
                log.debug("No NOC record found to validate with sourceRefId " + bpa.getApplicationNo());
            }
        }
    }
}
