package org.example;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class App {
    public static void main(String[] args) throws Exception {
        System.out.println("Program started.");
        Scanner scanner = new Scanner(System.in);
        Document doc = new Document(0, "document", "very useful document");
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 5);
        int command;
        int count = 0;
        while ((command = scanner.nextInt()) != 2) {
            if (command == 1) {
                count++;
                doc.setId(count);
                String response = crptApi.createProducedProductDocument(doc,
                        "signature",
                        CrptApi.ProductGroup.bicycle,
                        CrptApi.DocumentFormat.MANUAL,
                        "very simple token");
                System.out.println(response);
            } else {
                System.out.println("Wrong command");
            }
        }
        System.out.println("Program finished.");
    }

    @AllArgsConstructor
    @Setter
    @Getter
    private static class Document {
        private long id;
        private String name;
        private String description;
    }
}
