package nir.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ClassifierClient {

    private static final String CLASSIFIER_URL =
            System.getenv("CLASSIFIER_URL") != null
                    ? System.getenv("CLASSIFIER_URL")
                    : "http://localhost:8000/classify";

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public ClassificationResult classifyText(String text) throws IOException, InterruptedException {
        // Safely escape the text for JSON
        String jsonBody = mapper.writeValueAsString(new TextRequest(text));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CLASSIFIER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))   // was .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Classifier returned HTTP " + response.statusCode()
                    + ": " + response.body());
        }

        return mapper.readValue(response.body(), ClassificationResult.class);
    }

    // Inner DTO for serialisation
    private static class TextRequest {
        public final String text;
        TextRequest(String text) { this.text = text; }
    }
}
