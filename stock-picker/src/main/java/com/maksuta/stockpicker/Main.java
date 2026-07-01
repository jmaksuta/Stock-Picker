package com.maksuta.stockpicker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

public class Main {
    public static void main(String[] args) {

        Main main = new Main();
        main.run();
    }

    public void run() {
        String[] symbols = loadAllSymbols();
        printDisplay();
        try {

            String[][] batches = createBatches(symbols, 5);

            for (String[] batch : batches) {
                processBatch(batch);
            }

        } catch (Exception e) {
            print("Error fetching stock data: " + e.getMessage());
        }
    }

    private void processBatch(String[] symbols) throws IOException {
        Map<String, Stock> stocks = YahooFinance.get(symbols);

        for (String symbol : symbols) {
            Stock stock = stocks.get(symbol);

            if (stock == null || stock.getQuote().getPrice() == null) {
                // print("Stock data not found for symbol: " + symbol);
                continue;
            }

            BigDecimal price = stock.getQuote().getPrice();
            BigDecimal peRatio = stock.getStats().getPe();

            if (peRatio != null && peRatio.compareTo(new BigDecimal(25)) < 0) {
                BigDecimal fiftyDaySma = calculateFiftyDaySma(symbol);

                if (fiftyDaySma != null) {
                    if (price.compareTo(fiftyDaySma) > 0) {
                        String name = stock.getName();

                        if (name.length() > 22) {
                            name = name.substring(0, 22) + "...";
                        }
                        printf("%-10s %-25s %-11.2f %-10.2f\n", symbol, name, price, peRatio);
                    }
                }

            }

        }
    }

    private String[][] createBatches(String[] symbols, int batchSize) {
        List<String[]> result = new LinkedList<>();

        List<String> currentBatch = new LinkedList<>();
        for (int n = 0; n < symbols.length; n++) {
            if (currentBatch.size() + 1 <= batchSize) {
                currentBatch.add(symbols[n]);
            }
            if (currentBatch.size() == batchSize || n == symbols.length - 1) {
                result.add(currentBatch.toArray(new String[] {}));
                currentBatch = new LinkedList<>();
            }
        }
        return result.toArray(new String[][] {});
    }

    private String[] loadAllSymbols() {
        String[] result = new String[] {};
        String[] filePaths = new String[] {
                // "src/main/resources/nasdaq_screener_1782914027359-NYSE.csv",
                // "src/main/resources/nasdaq_screener_1782914069138-AMEX.csv",
                // "src/main/resources/nasdaq_screener_1782914089514-NASDAQ.csv"
                "nasdaq_screener_1782914027359-NYSE.csv",
                "nasdaq_screener_1782914069138-AMEX.csv",
                "nasdaq_screener_1782914089514-NASDAQ.csv"
        };
        for (String filePath : filePaths) {
            String[] symbols = loadSymbolsFromCsvFile(filePath);
            result = appendArrayStrings(result, symbols);
        }
        return result;
    }

    private String[] loadSymbolsFromFile(String filePath) {
        List<String> symbols = new LinkedList<>();
        // Implement logic to read symbols from a file and populate the symbols array
        try (FileReader fr = new FileReader(filePath);
                BufferedReader br = new BufferedReader(fr)) {
            for (String line; (line = br.readLine()) != null;) {
                if (!line.trim().isEmpty()) {
                    String[] parts = line.split(",");
                    String symbol = parts[0].trim();
                    symbols.add(symbol);
                }
            }

        } catch (IOException e) {
            print("Error reading symbols from file: " + e.getMessage());
            return new String[0];
        }
        return symbols.toArray(new String[] {});
    }

    private String[] loadSymbolsFromCsvFile(String filePath) {
        List<String> symbols = new LinkedList<>();
        String[][] csvData = readCsv(filePath, true);
        for (String[] row : csvData) {
            if (row.length > 0) {
                String symbol = row[0].trim();
                symbols.add(symbol);
            }
        }
        return symbols.toArray(new String[] {});
    }

    private String[][] readCsv(String fileName, boolean excludeHeader) {
        List<String[]> lines = new LinkedList<>();

        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(fileName)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("File not found in resources: " + fileName);
            }

            try (InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                    BufferedReader reader = new BufferedReader(streamReader)) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    if (excludeHeader && lineNumber == 0) {
                        lineNumber++;
                        continue;
                    }
                    lines.add(line.split(","));
                    lineNumber++;
                }

            }

        } catch (IOException e) {
            print("Error reading CSV file: " + e.getMessage());
        }
        return lines.toArray(new String[][] {});
    }

    private String[] appendArrayStrings(String[] original, String[] toAppend) {
        String[] result = new String[original.length + toAppend.length];
        System.arraycopy(original, 0, result, 0, original.length);
        System.arraycopy(toAppend, 0, result, original.length, toAppend.length);
        return result;
    }

    private BigDecimal calculateFiftyDaySma(String symbol) {
        BigDecimal result = null;
        try {
            Calendar from = Calendar.getInstance();
            Calendar to = Calendar.getInstance();
            from.add(Calendar.DAY_OF_MONTH, -80);

            Stock historySource = YahooFinance.get(symbol, from, to, Interval.DAILY);
            List<HistoricalQuote> quotes = historySource.getHistory();

            if (quotes.size() >= 50) {
                BigDecimal sum = BigDecimal.ZERO;
                int count = 0;
                for (int i = quotes.size() - 1; i >= 0 && count < 50; i--) {
                    BigDecimal closePrice = quotes.get(i).getClose();
                    if (closePrice != null) {
                        sum = sum.add(closePrice);
                        count++;
                    }
                }

                if (count != 0) {
                    result = sum.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP);
                }

            }

        } catch (Exception e) {
            print("Error calculating 50-day SMA for symbol: " + symbol + " - " + e.getMessage());
        }
        return result;
    }

    private void printDisplay() {
        print("Welcome to Stock Picker!");
        print(divider("-", 30));
        print("Scanning market Symbols for criteria...");
        print(divider("-", 30));
        print("Please wait...");
        print(divider("-", 30));
        printf("%-10s %-25s %-12s %-10s\n", "Ticker", "Company Name", "Price", "P/E Ratio");
        print(divider("-", 30));
    }

    private void print(String toPrint) {
        System.out.println(toPrint);
    }

    private void printf(String toPrint, Object... objects) {
        System.out.printf(toPrint, objects);
    }

    private String divider(String character, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(character);
        }
        return sb.toString();
    }
}