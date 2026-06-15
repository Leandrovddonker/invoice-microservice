alter table invoice_line
    drop constraint if exists invoice_line_line_type_check;

alter table invoice_line
    alter column line_type type varchar(255)
    using case line_type
        when 0 then 'LABOR'
        when 1 then 'PART'
        when 2 then 'SALE'
        else null
    end;

alter table invoice_line
    add constraint invoice_line_line_type_check
    check (line_type in ('LABOR', 'PART', 'SALE'));
