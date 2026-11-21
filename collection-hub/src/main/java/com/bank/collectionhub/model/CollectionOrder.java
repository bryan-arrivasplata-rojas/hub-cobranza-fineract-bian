package com.bank.collectionhub.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "collection_orders")
public class CollectionOrder {

    @Id
    private UUID id;

    @Column(name = "order_code", nullable = false, unique = true)
    private String orderCode;

    @Column(name = "last_batch_id", nullable = false)
    private UUID lastBatchId;

    @Column(name = "customer_external_id", nullable = false)
    private String customerExternalId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "loan_reference", nullable = false)
    private String loanReference;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(nullable = false)
    private String currency;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "pending_principal", nullable = false)
    private BigDecimal pendingPrincipal;

    @Column(name = "pending_interest", nullable = false)
    private BigDecimal pendingInterest;

    @Column(name = "total_due", nullable = false)
    private BigDecimal totalDue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CollectionOrder() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public UUID getLastBatchId() { return lastBatchId; }
    public void setLastBatchId(UUID lastBatchId) { this.lastBatchId = lastBatchId; }
    public String getCustomerExternalId() { return customerExternalId; }
    public void setCustomerExternalId(String customerExternalId) { this.customerExternalId = customerExternalId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getLoanReference() { return loanReference; }
    public void setLoanReference(String loanReference) { this.loanReference = loanReference; }
    public int getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(int installmentNumber) { this.installmentNumber = installmentNumber; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getPendingPrincipal() { return pendingPrincipal; }
    public void setPendingPrincipal(BigDecimal pendingPrincipal) { this.pendingPrincipal = pendingPrincipal; }
    public BigDecimal getPendingInterest() { return pendingInterest; }
    public void setPendingInterest(BigDecimal pendingInterest) { this.pendingInterest = pendingInterest; }
    public BigDecimal getTotalDue() { return totalDue; }
    public void setTotalDue(BigDecimal totalDue) { this.totalDue = totalDue; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
