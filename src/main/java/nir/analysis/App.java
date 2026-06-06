package nir.analysis;

import java.util.List;

public class App {
    public static void main(String[] args) {
        DBService db = new DBService();
        ClassifierClient classifier = new ClassifierClient();

        List<StatementRecord> records = db.getUnprocessedStatements();
        for (StatementRecord rec : records) {
            try {
                ClassificationResult result = classifier.classifyText(rec.getContent());
                db.saveAnalysisResult(rec.getId(), result);
                System.out.println("Processed: " + rec.getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
