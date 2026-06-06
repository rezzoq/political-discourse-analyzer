package nir.analysis;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBService {
    private static final String url = "jdbc:postgresql://localhost:5432/news_db";
    private final String user = "postgres";
    private final String password = System.getenv("DB_PASSWORD");

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public List<StatementRecord> getUnprocessedStatements() {
        List<StatementRecord> list = new ArrayList<>();
        String sql = "SELECT id, person_name, title, content FROM statements WHERE content IS NOT NULL";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new StatementRecord(
                        rs.getInt("id"),
                        rs.getString("person_name"),
                        rs.getString("title"),
                        rs.getString("content")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    public void saveAnalysisResult(int statementId, ClassificationResult result) {
        Predictions p = result.getPredictions();

        String sql = "INSERT INTO analysis_results (statement_id, sentiment, intention, formality/*, value_component, rhetorical_score, score*/) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (statement_id) DO NOTHING";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, statementId);
            pstmt.setInt(2, p.getEmotion());
            pstmt.setInt(3, p.getIntent());
            pstmt.setBoolean(4, p.isFormality());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}

