package com.amalitech.hilfe.models;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AgentGroupMemberId implements Serializable {
    private String agentId;
    private String agentGroupId;
}
