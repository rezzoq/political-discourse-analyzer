package nir.analysis;

import java.util.List;
import java.util.Map;

public class ClassificationResult {
    private Predictions predictions;
    private List<Map<String, Object>> stats_by_year;

    public Predictions getPredictions() {
        return predictions;
    }

    public void setPredictions(Predictions predictions) {
        this.predictions = predictions;
    }

    public List<Map<String, Object>> getStats_by_year() {
        return stats_by_year;
    }

    public void setStats_by_year(List<Map<String, Object>> stats_by_year) {
        this.stats_by_year = stats_by_year;
    }
}
