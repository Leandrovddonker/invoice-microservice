alter table invoice
    add column chargeback_request_status varchar(255) not null default 'NONE';

alter table invoice
    add column chargeback_requested_amount numeric(38, 2);

alter table invoice
    add column chargeback_request_reason varchar(255);

alter table invoice
    add column chargeback_requested_at timestamp;

alter table invoice
    add column chargeback_reviewed_at timestamp;
