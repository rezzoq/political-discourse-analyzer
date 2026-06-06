package nir.analysis;

public class StatementRecord {
    private final int id;
    private final String personName;
    private final String title;
    private final String content;

    public StatementRecord(int id, String personName, String title, String content) {
        this.id = id;
        this.personName = personName;
        this.title = title;
        this.content = content;
    }

    public int getId() {
        return id;
    }

    public String getPersonName() {
        return personName;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }
}
