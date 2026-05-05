package com.gpb.replication.sapiq.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Setter
public class SourceDbConnections implements Serializable {
    private String name;
    @JsonProperty("service_name")
    private String serviceName;
    @JsonProperty("db_type")
    private String dbType;
    private String url;
    private String username;
    private String password;
    private boolean active;
    private String schema;
    private int priority;

    @JsonIgnore
    public String getHostFromUrl() {
        if (url == null) return "unknown-host";
        try {
            Pattern pattern = Pattern.compile("://([^:/]+)");
            Matcher matcher = pattern.matcher(url);
            return matcher.find() ? matcher.group(1) : "unknown-host";
        } catch (Exception e) {
            return "unknown-host";
        }
    }

    @JsonIgnore
    public int getPortFromUrl() {
        if (url == null) return -1;
        try {
            Pattern pattern = Pattern.compile(":(\\d+)");
            Matcher matcher = pattern.matcher(url);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1521;
        } catch (Exception e) {
            return 1521;
        }
    }
    @JsonIgnore
    public String getDnsFromUrl() {
        if (url == null) return "unknown-dns";
        try {
            URI uri = URI.create(url.replace("jdbc:", ""));
            return uri.getHost() != null ? uri.getHost() : "unknown-dns";
        } catch (Exception e) {
            return "unknown-dns";
        }
    }

}
