/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonProperty
 *  javax.validation.Valid
 *  javax.validation.constraints.NotNull
 *  org.egov.common.contract.request.RequestInfo
 */
package org.egov.bpa.web.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.egov.bpa.web.model.CalulationCriteria;
import org.egov.common.contract.request.RequestInfo;

public class CalculationReq {
    @JsonProperty(value="RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo;
    @JsonProperty(value="CalulationCriteria")
    @Valid
    private List<CalulationCriteria> calulationCriteria;

    public static CalculationReqBuilder builder() {
        return new CalculationReqBuilder();
    }

    public RequestInfo getRequestInfo() {
        return this.requestInfo;
    }

    public List<CalulationCriteria> getCalulationCriteria() {
        return this.calulationCriteria;
    }

    @JsonProperty(value="RequestInfo")
    public void setRequestInfo(RequestInfo requestInfo) {
        this.requestInfo = requestInfo;
    }

    @JsonProperty(value="CalulationCriteria")
    public void setCalulationCriteria(List<CalulationCriteria> calulationCriteria) {
        this.calulationCriteria = calulationCriteria;
    }

    public CalculationReq(RequestInfo requestInfo, List<CalulationCriteria> calulationCriteria) {
        this.requestInfo = requestInfo;
        this.calulationCriteria = calulationCriteria;
    }

    public CalculationReq() {
    }

    public static class CalculationReqBuilder {
        private RequestInfo requestInfo;
        private List<CalulationCriteria> calulationCriteria;

        CalculationReqBuilder() {
        }

        @JsonProperty(value="RequestInfo")
        public CalculationReqBuilder requestInfo(RequestInfo requestInfo) {
            this.requestInfo = requestInfo;
            return this;
        }

        @JsonProperty(value="CalulationCriteria")
        public CalculationReqBuilder calulationCriteria(List<CalulationCriteria> calulationCriteria) {
            this.calulationCriteria = calulationCriteria;
            return this;
        }

        public CalculationReq build() {
            return new CalculationReq(this.requestInfo, this.calulationCriteria);
        }

        public String toString() {
            return "CalculationReq.CalculationReqBuilder(requestInfo=" + this.requestInfo + ", calulationCriteria=" + this.calulationCriteria + ")";
        }
    }
}
