package com.gpb.replication.sapiq.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class EntityId implements Serializable{
    @Column(name = "id")
    private Long id;

    @Column(name = "parent_fqn")
    private String parentFqn;

    public EntityId(Long id, String parentFqn) {
        this.id = id;
        this.parentFqn = parentFqn;
    }

    public EntityId() {
    }
}
