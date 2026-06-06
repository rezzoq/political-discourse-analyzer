package nir.analysis;

public class Predictions {
    private int emotion;
    private int intent;
    private boolean formality;

    // геттеры и сеттеры
    public int getEmotion() { return emotion; }
    public void setEmotion(int emotion) { this.emotion = emotion; }

    public int getIntent() { return intent; }
    public void setIntent(int intent) { this.intent = intent; }

    public boolean isFormality() { return formality; }
    public void setFormality(boolean formality) { this.formality = formality; }
}
