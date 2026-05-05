package com.gpb.replication.sapiq.log;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
public class SvoiJournal {
    private static final String UNKNOW_VALUE_PLACEHOLDER = "unknow";
    private static final char SVOI_DELIMETER = '|';
    private static final String CEF_DATA = "CEF:0";
    private static final String EQUALS = "=";
    private static final String SPACE = " ";
    private DateTimeFormatter sdf;
    private static final String DEVICE_VENDOR = "OmniPlatform";
    private String time;
    private String deviceProduct;
    private String deviceVersion;
    private String deviceEventClassID;
    private String name;
    private String message;
    private SvoiSeverityEnum severity;
    private final Long externalId;
    private String deviceProcessName;
    private Integer spt;
    private String app;
    private String start;
    private Integer dpt;
    private String end;
    private String rt;
    private SvoiOutcomeEnum outcome;
    private String suser;
    private String sntdom;
    private String src;
    private String smac;
    private String shost;
    private String duser;
    private String dntdom;
    private String dst;
    private String dmac;
    private String dhost;
    private String dvchost;
    private String hostForSvoi;

    public SvoiJournal(Long externalId, DateTimeFormatter sdf) {
        this.severity = SvoiSeverityEnum.ONE;
        this.outcome = SvoiOutcomeEnum.SUCCESS;
        this.externalId = externalId;
        this.sdf = sdf;
    }

    private void addExternalParameter(final String paramName, String paramValue, StringBuilder stringBuilder) {
        if (!this.isEmpty(paramValue) && !"null".equalsIgnoreCase(paramValue)) {
            stringBuilder.append(paramName).append("=").append(paramValue).append(" ");
        } else {
            stringBuilder.append(paramName).append("=").append(" ");
        }
    }

    private String getExtension() {
        StringBuilder stringBuilder = new StringBuilder();
        this.addExternalParameter("externalId", this.externalId.toString(), stringBuilder);
        this.addExternalParameter("suser", this.suser, stringBuilder);
        this.addExternalParameter("sntdom", this.sntdom, stringBuilder);
        this.addExternalParameter("src", this.src, stringBuilder);
        this.addExternalParameter("smac", this.smac, stringBuilder);
        this.addExternalParameter("shost", this.shost, stringBuilder);
        this.addExternalParameter("duser", this.duser, stringBuilder);
        this.addExternalParameter("dntdom", this.dntdom, stringBuilder);
        this.addExternalParameter("dst", this.dst, stringBuilder);
        this.addExternalParameter("dmac", this.dmac, stringBuilder);
        this.addExternalParameter("dhost", this.dhost, stringBuilder);
        this.addExternalParameter("dvchost", this.dvchost, stringBuilder);
        this.addExternalParameter("spt", "" + this.spt, stringBuilder);
        this.addExternalParameter("dpt", "" + this.dpt, stringBuilder);
        this.addExternalParameter("app", this.app, stringBuilder);
        this.addExternalParameter("start", this.start, stringBuilder);
        this.addExternalParameter("end", this.end, stringBuilder);
        this.addExternalParameter("rt", this.rt, stringBuilder);
        this.addExternalParameter("msg", this.message, stringBuilder);
        this.addExternalParameter("deviceProcessName", this.deviceProcessName, stringBuilder);
        this.addExternalParameter("outcome", this.outcome.getValue(), stringBuilder);
        return stringBuilder.toString().trim();
    }

    public String toString() {
        String emptyParam = null;
        if (this.isEmpty(this.time)) {
            emptyParam = "time";
        } else if (this.isEmpty(this.deviceProduct)) {
            emptyParam = "deviceProduct";
        } else if (this.isEmpty(this.deviceVersion)) {
            emptyParam = "deviceVersion";
        } else if (this.isEmpty(this.deviceEventClassID)) {
            emptyParam = "deviceEventClassID";
        } else if (this.isEmpty(this.name)) {
            emptyParam = "name";
        } else if (this.isEmpty(this.message)) {
            emptyParam = "message";
        }

        if (emptyParam != null) {
            throw new IllegalArgumentException(String.format("Обязательный парамерт %s пуст, поэтому нельзя вывести строковое представление записи.", emptyParam));
        } else {
            String var = this.deviceProduct;
            return "CEF:0|OmniPlatform|" + var + "|" + this.deviceVersion + "|" + this.deviceEventClassID + "|" + this.name + "|" + this.severity.getValue() + "|" + getExtension();
        }
    }

    public void setTime(LocalDateTime time) {
        this.time = this.sdf.format(time);
    }

    public void setDeviceProduct(String deviceProduct) {
        if (deviceProduct != null)
            this.deviceProduct = deviceProduct;
    }

    public void setDeviceVersion(String deviceVersion) {
        if (deviceVersion != null)
            this.deviceVersion = deviceVersion;
    }
    public void setStart(LocalDateTime start) {
        this.start = this.sdf.format(start);
    }

    public void setEnd(LocalDateTime end) {
        this.end = this.sdf.format(end);
    }

    public void setRt(LocalDateTime rt) {
        this.rt = this.sdf.format(rt);
    }

    public String getDeviceProcessName() {
        return this.deviceProcessName != null ? this.deviceProcessName : "unknown";
    }
    private boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
