package com.capstone.fraudcase.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import org.h2.api.Trigger;

/** Database-level guard: rejects UPDATE and DELETE on the audit_event table. */
public class AppendOnlyAuditTrigger implements Trigger {

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("audit_event is append-only; updates and deletes are not permitted");
    }
}
