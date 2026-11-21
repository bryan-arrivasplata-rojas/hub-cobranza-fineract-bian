package com.bank.collectionhub.adapter;

import com.bank.collectionhub.model.BillingBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BatchRepository extends JpaRepository<BillingBatch, UUID> {
}
