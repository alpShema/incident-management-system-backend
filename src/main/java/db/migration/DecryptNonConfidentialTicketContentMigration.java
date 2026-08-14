package db.migration;

import com.amalitech.hilfe.crypto.AesGcmCipher;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Flyway migration V81: remediation for environments that already ran an earlier version of V80
 * ({@link EncryptExistingTicketContentMigration}), which unconditionally encrypted every
 * incident's title/description/note body/message content instead of only confidential ones.
 * Decrypts any such value back to plaintext -- {@code v1:}-prefixed AND belonging to a
 * non-confidential incident. Because that earlier V80 has a {@code null} checksum, Flyway can't
 * detect that its logic changed, so already-migrated environments never re-run it; this migration
 * is what actually fixes them.
 * <p>
 * Safe everywhere else too: on an environment where the corrected V80 already ran (or where V80
 * hasn't run yet), this matches zero rows and is a no-op.
 */
public class DecryptNonConfidentialTicketContentMigration implements JavaMigration {

    private static final int BATCH_SIZE = 500;

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("81");
    }

    @Override
    public String getDescription() {
        return "Decrypt non-confidential ticket content";
    }

    @Override
    public Integer getChecksum() {
        return null;
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    public void migrate(Context context) throws SQLException {
        String fieldKey = System.getenv("FIELD_ENCRYPTION_KEY");
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalStateException("FIELD_ENCRYPTION_KEY must be set before this migration can run.");
        }
        AesGcmCipher cipher = AesGcmCipher.fromPassphrase(fieldKey);
        Connection connection = context.getConnection();

        decryptColumn(connection, cipher,
                """
                SELECT i.id, i.title FROM "Incident" i
                JOIN "IncidentType" it ON it.id = i.incident_type_id
                WHERE it.confidential = false AND i.title LIKE 'v1:%'
                ORDER BY i.id LIMIT """ + BATCH_SIZE,
                "UPDATE \"Incident\" SET title = ? WHERE id = ?");
        decryptColumn(connection, cipher,
                """
                SELECT i.id, i.description FROM "Incident" i
                JOIN "IncidentType" it ON it.id = i.incident_type_id
                WHERE it.confidential = false AND i.description LIKE 'v1:%'
                ORDER BY i.id LIMIT """ + BATCH_SIZE,
                "UPDATE \"Incident\" SET description = ? WHERE id = ?");
        decryptColumn(connection, cipher,
                """
                SELECT n.id, n.body FROM "InternalNote" n
                JOIN "Incident" i ON i.id = n.incident_id
                JOIN "IncidentType" it ON it.id = i.incident_type_id
                WHERE it.confidential = false AND n.body LIKE 'v1:%'
                ORDER BY n.id LIMIT """ + BATCH_SIZE,
                "UPDATE \"InternalNote\" SET body = ? WHERE id = ?");
        decryptColumn(connection, cipher,
                """
                SELECT m.id, m.content FROM "Message" m
                JOIN "Incident" i ON i.id = m.incident_id
                JOIN "IncidentType" it ON it.id = i.incident_type_id
                WHERE it.confidential = false AND m.content LIKE 'v1:%'
                ORDER BY m.id LIMIT """ + BATCH_SIZE,
                "UPDATE \"Message\" SET content = ? WHERE id = ?");
    }

    // Mirror of EncryptExistingTicketContentMigration#encryptColumn, decrypting instead. Each
    // batch only ever sees still-encrypted rows on non-confidential incidents (the SELECT already
    // filters to v1:-prefixed values), so this naturally converges and is safe to interrupt and
    // re-run.
    private void decryptColumn(Connection connection, AesGcmCipher cipher, String selectSql, String updateSql) throws SQLException {
        while (true) {
            List<String[]> rows = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(selectSql);
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rows.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }
            if (rows.isEmpty()) {
                return;
            }
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                for (String[] row : rows) {
                    update.setString(1, cipher.decrypt(row[1]));
                    update.setString(2, row[0]);
                    update.addBatch();
                }
                update.executeBatch();
            }
        }
    }
}
