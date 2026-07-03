package com.archives;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Scanner;

public class App {

    public static void main(String[] args) throws IOException {
        Path dataFile = Paths.get(args.length > 0 ? args[0] : "data/rows.db");

        System.out.println("db-demo engine");
        System.out.println("  data file : " + dataFile.toAbsolutePath());

        try (Engine engine = Engine.open(dataFile)) {
            System.out.println("  recovered : " + engine.rowCount() + " row(s)");
            System.out.println();
            printHelp();

            Scanner in = new Scanner(System.in);
            while (true) {
                System.out.print("db> ");
                if (!in.hasNextLine()) break;
                String line = in.nextLine().trim();
                if (line.isEmpty()) continue;
                if (!handle(engine, line)) break;
            }
            System.out.println("bye.");
        }
    }

    private static boolean handle(Engine engine, String line) throws IOException {
        String[] parts = line.split("\\s+", 3);
        String cmd = parts[0].toUpperCase();
        try {
            switch (cmd) {
                case "INSERT" -> {
                    if (parts.length < 3) {
                        System.out.println("usage: INSERT <id> <name>");
                        return true;
                    }
                    long id = Long.parseLong(parts[1]);
                    engine.insert(new Row(id, parts[2]));
                    System.out.println("OK (1 row)");
                }
                case "SELECT", "GET" -> {
                    if (parts.length < 2) {
                        System.out.println("usage: SELECT <id>");
                        return true;
                    }
                    long id = Long.parseLong(parts[1]);
                    Optional<Row> row = engine.query(id);
                    if (row.isEmpty()) {
                        System.out.println("(0 rows)");
                    } else {
                        System.out.println(row.get().id() + " | " + row.get().name());
                    }
                }
                case "COUNT" -> System.out.println(engine.rowCount() + " row(s)");
                case "HELP", "?" -> printHelp();
                case "EXIT", "QUIT" -> {
                    return false;
                }
                default -> System.out.println("unknown command: " + cmd + " (try HELP)");
            }
        } catch (NumberFormatException e) {
            System.out.println("error: id must be a number");
        } catch (Engine.PrimaryKeyViolation e) {
            System.out.println("error: " + e.getMessage());
        }
        return true;
    }

    private static void printHelp() {
        System.out.println("commands:");
        System.out.println("  INSERT <id> <name>   insert a row (id is the primary key)");
        System.out.println("  SELECT <id>          fetch a row by id");
        System.out.println("  COUNT                show row count");
        System.out.println("  HELP                 show this help");
        System.out.println("  EXIT                 close engine and quit");
    }
}