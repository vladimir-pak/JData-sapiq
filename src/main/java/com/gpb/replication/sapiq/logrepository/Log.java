package com.gpb.replication.sapiq.logrepository;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Log {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Temporal(TemporalType.TIMESTAMP)
    private LocalDateTime created;
    private String log;
    private String type;

    public Log(LocalDateTime created, String log, String type) {
        this.created = created;
        this.log = log;
        this.type = type;
    }
}
