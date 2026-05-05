package com.gpb.replication.sapiq.log;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.boot.context.event.ApplicationStartedEvent;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SvoiJournalFactory {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");    private String deviceProduct;
    private String deviceVersion;
    @Value("$server.port")
    private Integer localPort;
    private String localHostName;
    private Long journalLineNumber = 0L;

    @Autowired(required = false)
    private BuildProperties buildProperties;

    public SvoiJournal getJournalSource() {
        SvoiJournal svoiJournal = this.getBaseJournal();
        svoiJournal.setDvchost(this.getLocalHostName());
        svoiJournal.setStart(LocalDateTime.now());
        return svoiJournal;
    }

    private SvoiJournal getBaseJournal() {
        LocalDateTime now = LocalDateTime.now();
        SvoiJournal svoiJournal = new SvoiJournal(this.nextLineNumber(), DATE_FORMATTER);
        svoiJournal.setDeviceProduct(this.buildProperties != null ? this.buildProperties.getName() : this.deviceProduct);
        svoiJournal.setDeviceVersion(this.buildProperties != null ? this.buildProperties.getVersion() : this.deviceVersion);
        svoiJournal.setTime(now);
        svoiJournal.setApp("TCP");
        svoiJournal.setRt(now);
        svoiJournal.setDeviceProcessName("java");
        return svoiJournal;
    }

    private synchronized Long nextLineNumber() {
        return this.journalLineNumber = this.journalLineNumber + 1L;
    }

    @EventListener({ApplicationStartedEvent.class})
    @Order(Integer.MIN_VALUE)
    public void init() {
        try {
            this.localHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            this.localHostName = InetAddress.getLoopbackAddress().getHostName();
        }
    }
}

