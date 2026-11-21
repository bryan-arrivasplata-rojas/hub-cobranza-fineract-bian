CREATE TABLE billing_batches (
    id UUID PRIMARY KEY,
    batch_code VARCHAR(50) NOT NULL UNIQUE,
    cut_off_date DATE NOT NULL,
    horizon_end_date DATE NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_orders_processed INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL
);

CREATE TABLE collection_orders (
    id UUID PRIMARY KEY,
    order_code VARCHAR(60) NOT NULL UNIQUE,
    last_batch_id UUID NOT NULL REFERENCES billing_batches(id),
    customer_external_id VARCHAR(60) NOT NULL,
    customer_name VARCHAR(150) NOT NULL,
    loan_reference VARCHAR(60) NOT NULL,
    installment_number INTEGER NOT NULL,
    currency VARCHAR(10) NOT NULL,
    due_date DATE NOT NULL,
    pending_principal NUMERIC(15, 2) NOT NULL,
    pending_interest NUMERIC(15, 2) NOT NULL,
    total_due NUMERIC(15, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_loan_installment UNIQUE (loan_reference, installment_number)
);

CREATE INDEX idx_orders_loan_ref ON collection_orders(loan_reference);
CREATE INDEX idx_orders_due_date ON collection_orders(due_date);
CREATE INDEX idx_orders_status ON collection_orders(status);
