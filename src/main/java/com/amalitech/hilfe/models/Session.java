package com.amalitech.hilfe.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "Session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String sid;

    @Column(nullable = false)
    private String data;

    @Column(name = "\"expiresAt\"", nullable = false)
    private Instant expiresAt;
}
