package uj.wmii.pwj.w7.insurance;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FloridaInsurance {

    public static void main(String[] args) {
        String zipFilePath = "FL_insurance.csv.zip";

        try {
            List<InsuranceEntry> data = loadData(zipFilePath);
            generateCountFile(data);
            generateTiv2012File(data);
            generateMostValuableFile(data);
            System.out.println("Przetwarzanie zakończone sukcesem.");

        } catch (IOException e) {
            System.err.println("Błąd podczas operacji wejścia/wyjścia: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<InsuranceEntry> loadData(String zipFilePath) throws IOException {
        List<InsuranceEntry> entries = new ArrayList<>();

        try (ZipFile zipFile = new ZipFile(zipFilePath)) {

            ZipEntry entry = zipFile.entries().hasMoreElements() ? zipFile.entries().nextElement() : null;

            if (entry == null) {
                throw new FileNotFoundException("Archiwum ZIP jest puste.");
            }

            try (InputStream is = zipFile.getInputStream(entry);
                 BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {


                String headerLine = br.readLine();
                if (headerLine == null) return entries;

                String[] headers = headerLine.split(",");
                Map<String, Integer> colMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    colMap.put(headers[i].trim(), i);
                }


                int countyIdx = colMap.getOrDefault("county", -1);
                int tiv2011Idx = colMap.getOrDefault("tiv_2011", -1);
                int tiv2012Idx = colMap.getOrDefault("tiv_2012", -1);

                if (countyIdx == -1 || tiv2011Idx == -1 || tiv2012Idx == -1) {
                    throw new IOException("Nie znaleziono wymaganych kolumn w pliku CSV.");
                }

                entries = br.lines()
                        .map(line -> line.split(","))
                        .filter(arr -> arr.length > Math.max(tiv2012Idx, tiv2011Idx))
                        .map(arr -> {
                            try {
                                String county = arr[countyIdx].trim();
                                double tiv2011 = Double.parseDouble(arr[tiv2011Idx]);
                                double tiv2012 = Double.parseDouble(arr[tiv2012Idx]);
                                return new InsuranceEntry(county, tiv2011, tiv2012);
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        }
        return entries;
    }

    private static void generateCountFile(List<InsuranceEntry> data) throws IOException {
        long count = data.stream()
                .map(InsuranceEntry::getCounty)
                .distinct()
                .count();


        writeToFile("count.txt", String.valueOf(count));
    }

    private static void generateTiv2012File(List<InsuranceEntry> data) throws IOException {
        BigDecimal totalTiv2012 = data.stream()
                .map(entry -> BigDecimal.valueOf(entry.getTiv2012()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String formattedValue = String.format(Locale.US, "%.2f", totalTiv2012);


        writeToFile("tiv2012.txt", formattedValue);
    }

    private static void generateMostValuableFile(List<InsuranceEntry> data) throws IOException {
        String content = data.stream()
                .collect(Collectors.groupingBy(
                        InsuranceEntry::getCounty,
                        Collectors.mapping(
                                e -> BigDecimal.valueOf(e.getTiv2012()).subtract(BigDecimal.valueOf(e.getTiv2011())),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                        )
                ))
                .entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .map(e -> String.format(Locale.US, "%s,%.2f",
                        e.getKey(),
                        e.getValue().setScale(2, RoundingMode.HALF_UP)))
                .collect(Collectors.joining("\n"));


        writeToFile("most_valuable.txt", "country,value\n" + content);
    }


    private static void writeToFile(String fileName, String content) throws IOException {
        Path path = Paths.get(fileName);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    public static class InsuranceEntry {
        private final String county;
        private final double tiv2011;
        private final double tiv2012;

        public InsuranceEntry(String county, double tiv2011, double tiv2012) {
            this.county = county;
            this.tiv2011 = tiv2011;
            this.tiv2012 = tiv2012;
        }

        public String getCounty() {
            return county;
        }

        public double getTiv2011() {
            return tiv2011;
        }

        public double getTiv2012() {
            return tiv2012;
        }
    }
}