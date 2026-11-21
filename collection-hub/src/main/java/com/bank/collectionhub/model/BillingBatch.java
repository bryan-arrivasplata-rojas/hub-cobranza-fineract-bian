package com.bank.collectionhub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "billing_batches")
public class BillingBatch {

    @Id
    private UUID id;

    @Column(name = "batch_code", nullable = false, unique = true)
    private String batchCode;

    @Column(name = "cut_off_date", nullable = false)
    private LocalDate cutOffDate;

    @Column(name = "horizon_end_date", nullable = false)
    private LocalDate horizonEndDate;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "total_orders_processed", nullable = false)
    private int totalOrdersProcessed;

    @Column(nullable = false)
    private String status;

    public BillingBatch() {
    }

    public BillingBatch(UUID id, String batchCode, LocalDate cutOffDate, LocalDate horizonEndDate,
                        Instant executedAt, int totalOrdersProcessed, String status) {
        this.id = id;
        this.batchCode = batchCode;
        this.cutOffDate = cutOffDate;
        this.horizonEndDate = horizonEndDate;
        this.executedAt = executedAt;
        this.totalOrdersProcessed = totalOrdersProcessed;
        this.status = status;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public LocalDate getCutOffDate() { return cutOffDate; }
    public void setCutOffDate(LocalDate cutOffDate) { this.cutOffDate = cutOffDate; }
    public LocalDate getHorizonEndDate() { return horizonEndDate; }
    public void setHorizonEndDate(LocalDate horizonEndDate) { this.horizonEndDate = horizonEndDate; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public int getTotalOrdersProcessed() { return totalOrdersProcessed; }
    public void setTotalOrdersProcessed(int totalOrdersProcessed) { this.totalOrdersProcessed = totalOrdersProcessed; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
