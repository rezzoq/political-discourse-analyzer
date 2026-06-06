package nir.parsing;

import java.sql.*;

public class StatementSaver {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/news_db";
    private static final String USER = "postgres";
    private static final String PASSWORD = System.getenv("DB_PASSWORD");

    public static void saveStatement(String personName, String title, String content,
                                     String url, String source, Timestamp publishedAt) {
        String sql = "INSERT INTO statements (person_name, title, content, url, source, published_at) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (url) DO NOTHING";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, personName);
            stmt.setString(2, title);
            stmt.setString(3, content != null ? content : "");
            stmt.setString(4, url);
            stmt.setString(5, source);
            stmt.setTimestamp(6, publishedAt);

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ Saved: " + title);
            } else {
                System.out.println("⏭️ Duplicate skipped: " + url);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
