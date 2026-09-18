package org.example.repository;

import org.example.model.StockEvent;

import java.sql.*;

public class StockEventDao {

    private final Connection conn;

    public StockEventDao(Connection connection) {
        this.conn = connection;
    }

    public void save(StockEvent event) throws SQLException {

        String sql = """
            INSERT INTO stock_events (
                symbol,
                event_time,
                event_date,
                event_type,
                title,
                description,
                source,
                source_url,
                sentiment,
                sentiment_score,
                importance_score,
                content_hash,
                document_summary
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            
            ON CONFLICT (content_hash)
            DO UPDATE SET
                document_summary = EXCLUDED.document_summary,
                sentiment = EXCLUDED.sentiment,
                sentiment_score = EXCLUDED.sentiment_score,
                importance_score = EXCLUDED.importance_score,
                updated_at = CURRENT_TIMESTAMP
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, event.getSymbol());

            ps.setTimestamp(2, Timestamp.valueOf(event.getEventTime()));

            ps.setDate(3, Date.valueOf(event.getEventDate()));

            ps.setString(4, event.getEventType());

            ps.setString(5, event.getTitle());

            ps.setString(6, event.getDescription());

            ps.setString(7, event.getSource());

            ps.setString(8, event.getSourceUrl());

            ps.setString(9, event.getSentiment());

            ps.setDouble(10, event.getSentimentScore());

            ps.setDouble(11, event.getImportanceScore());

            ps.setString(12, event.getContentHash());

            ps.setString(13, event.getDocumentSummary());

            ps.executeUpdate();

            conn.commit();
        }
    }
}
