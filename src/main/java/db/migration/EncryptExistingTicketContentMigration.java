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
 * Flyway migration V80: one-time re-encryption of ticket content (Incident.title/description,
 * InternalNote.body, Message.content) written before field-level encryption existed. Idempotent
 * and resumable: any value already carrying {@link AesGcmCipher#VERSION_PREFIX} is excluded from
 * the SELECT, so a partial run picks up exactly where it left off on the next attempt. Raw JDBC
 * only -- this runs before the Spring context/EntityManagerFactory exists, so it can't use the
 * JPA converter.
 * <p>
 * Implements {@link JavaMigration} directly (rather than extending {@code BaseJavaMigration})
 * specifically so the class name doesn't have to follow Flyway's {@code V<version>__<description>}
 * convention -- version/description are supplied explicitly below instead. Flyway discovers Java
 * migrations by scanning for classes assignable to {@link JavaMigration} within the configured
 * locations, not by parsing class names, so this is picked up the same way V79 or a
 * BaseJavaMigration subclass would be.
 */
public class EncryptExistingTicketContentMigration implements JavaMigration {

    private static final int BATCH_SIZE = 500;

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("80");
    }

    @Override
    public String getDescription() {
        return "Encrypt existing ticket content";
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

        encryptColumn(connection, cipher,
                "SELECT id, title FROM \"Incident\" WHERE title IS NOT NULL AND title NOT LIKE 'v1:%' ORDER BY id LIMIT " + BATCH_SIZE,
                "UPDATE \"Incident\" SET title = ? WHERE id = ?");
        encryptColumn(connection, cipher,
                "SELECT id, description FROM \"Incident\" WHERE description IS NOT NULL AND description NOT LIKE 'v1:%' ORDER BY id LIMIT " + BATCH_SIZE,
                "UPDATE \"Incident\" SET description = ? WHERE id = ?");
        encryptColumn(connection, cipher,
                "SELECT id, body FROM \"InternalNote\" WHERE body IS NOT NULL AND body NOT LIKE 'v1:%' ORDER BY id LIMIT " + BATCH_SIZE,
                "UPDATE \"InternalNote\" SET body = ? WHERE id = ?");
        encryptColumn(connection, cipher,
                "SELECT id, content FROM \"Message\" WHERE content IS NOT NULL AND content NOT LIKE 'v1:%' ORDER BY id LIMIT " + BATCH_SIZE,
                "UPDATE \"Message\" SET content = ? WHERE id = ?");
    }

    // Repeats the same bounded SELECT/UPDATE pair until a batch comes back empty. Each batch only
    // ever sees still-unencrypted rows (the SELECT excludes anything already "v1:"-prefixed), so
    // this naturally converges and is safe to interrupt and re-run.
    private void encryptColumn(Connection connection, AesGcmCipher cipher, String selectSql, String updateSql) throws SQLException {
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
                    update.setString(1, cipher.encrypt(row[1]));
                    update.setString(2, row[0]);
                    update.addBatch();
                }
                update.executeBatch();
            }
        }
    }
}
