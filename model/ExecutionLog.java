package com.example.itopshealthcheck.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@NoArgsConstructor
public class ExecutionLog {
    private Date startTime;
    private Date endTime;
    private String output;
    private Status status;
    private String triggeredBy;

    public ExecutionLog(String triggeredBy) {
        this.startTime = new Date();
        this.status = Status.PENDING;
        this.triggeredBy = triggeredBy;
    }
}