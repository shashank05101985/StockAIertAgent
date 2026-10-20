package org.example.repository;

import org.example.model.CorporateAction;

import java.sql.*;
import java.util.*;

public class CorporateActionRepository {

    private final Connection connection;

    public CorporateActionRepository(Connection connection) {
        this.connection = connection;
    }

    /** All actions, per symbol, oldest ex-date first. */
    public Map<String, List<CorporateAction>> loadAll() throws SQLException {
        return query("SELECT symbol, ex_date, action, factor, purpose FROM corporate_actions ORDER BY symbol, ex_date");
    }

    /** Actions whose feature rows have not been rebuilt yet. */
    public Map<String, List<CorporateAction>> loadUnapplied() throws SQLException {
        return query("SELECT symbol, ex_date, action, factor, purpose FROM corporate_actions WHERE applied_at IS NULL ORDER BY symbol, ex_date");
    }

    private Map<String, List<CorporateAction>> query(String sql) throws SQLException {

        Map<String, List<CorporateAction>> result = new HashMap<>();

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                CorporateAction a = new CorporateAction(
                    rs.getString("symbol"),
                    rs.getDate("ex_date").toLocalDate(),
                    rs.getString("action"),
                    rs.getDouble("factor"),
                    rs.getString("purpose"));

                result.computeIfAbsent(a.symbol(), k -> new ArrayList<>()).add(a);
            }
        }

        // End the read transaction (autocommit is off on the shared connection).
        connection.commit();

        return result;
    }

    public void markApplied(Collection<CorporateAction> actions) throws SQLException {

        if (actions.isEmpty()) {
            return;
        }

        try (PreparedStatement ps = connection.prepareStatement(
            "UPDATE corporate_actions SET applied_at = now() WHERE symbol = ? AND ex_date = ?")) {

            for (CorporateAction a : actions) {
                ps.setString(1, a.symbol());
                ps.setDate(2, java.sql.Date.valueOf(a.exDate()));
                ps.addBatch();
            }

            ps.executeBatch();
            connection.commit();

        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        }
    }

    /**
     * Insert or update actions. If the factor of an existing action changes,
     * applied_at is cleared so the next feature run rebuilds that symbol.
     */
    public int upsert(Collection<CorporateAction> actions) throws SQLException {

        String sql = """
            INSERT INTO corporate_actions (symbol, ex_date, action, factor, purpose)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (symbol, ex_date) DO UPDATE SET
                action = EXCLUDED.action,
                factor = EXCLUDED.factor,
                purpose = EXCLUDED.purpose,
                applied_at = CASE
                    WHEN corporate_actions.factor IS DISTINCT FROM EXCLUDED.factor THEN NULL
                    ELSE corporate_actions.applied_at
                END
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (CorporateAction a : actions) {
                ps.setString(1, a.symbol());
                ps.setDate(2, java.sql.Date.valueOf(a.exDate()));
                ps.setString(3, a.action());
                ps.setDouble(4, a.factor());
                ps.setString(5, a.purpose());
                ps.addBatch();
            }

            int[] counts = ps.executeBatch();
            connection.commit();
            return counts.length;

        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        }
    }
}
