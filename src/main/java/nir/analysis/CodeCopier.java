package nir.analysis;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Класс для объединения всех Python-файлов из заданной папки (рекурсивно)
 * в один общий текстовый документ. Перед содержимым каждого файла добавляется
 * его имя (относительный путь от исходной папки).
 */
public class CodeCopier {

    /**
     * Объединяет все .py файлы из sourceDir в один выходной файл outputFile.
     * Каждый блок начинается с комментария с именем файла.
     *
     * @param sourceDir путь к исходной папке (содержит .py файлы и подпапки)
     * @param outputFile путь к итоговому файлу (например, "merged_python_code.txt")
     * @throws IOException если ошибка ввода-вывода
     */
    public static void mergePythonFiles(Path sourceDir, Path outputFile) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Исходный путь не является папкой: " + sourceDir);
        }

        // Собираем все .py файлы рекурсивно
        List<Path> pyFiles;
        try (var stream = Files.walk(sourceDir)) {
            pyFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".py"))
                    .sorted() // сортируем для воспроизводимого порядка
                    .collect(Collectors.toList());
        }

        if (pyFiles.isEmpty()) {
            System.out.println("Python-файлы не найдены в " + sourceDir);
            return;
        }

        // Создаём родительскую папку для выходного файла, если нужно
      //  Files.createDirectories(outputFile.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            for (Path pyFile : pyFiles) {
                // Относительный путь от sourceDir (читаемое имя)
                Path relativePath = sourceDir.relativize(pyFile);

                // Заголовок файла
                writer.write("=" .repeat(80));
                writer.newLine();
                writer.write("Файл: " + relativePath.toString());
                writer.newLine();
                writer.write("=" .repeat(80));
                writer.newLine();
                writer.newLine(); // пустая строка для разделения

                // Копируем содержимое .py файла
                try (BufferedReader reader = Files.newBufferedReader(pyFile)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                } catch (IOException e) {
                    writer.write("Ошибка при чтении файла: " + e.getMessage());
                    writer.newLine();
                }

                writer.newLine(); // дополнительная пустая строка между файлами
            }
        }

        System.out.println("Объединение завершено. Результат: " + outputFile.toAbsolutePath());
        System.out.println("Обработано файлов: " + pyFiles.size());
    }

    public static void main(String[] args) {
        Path sourceDir = Path.of("src/main/java/nir/analysis/python");
        Path outputFile = Path.of("savedPython.txt");   // сохранится в корне проекта


        try {
            mergePythonFiles(sourceDir, outputFile);
        } catch (IOException e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
