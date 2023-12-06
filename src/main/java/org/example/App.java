package org.example;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws Exception {
        //piece of code to test in the intermediate stage
        System.out.println("Hello from main.");
//        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 5);
        Scanner scanner = new Scanner(System.in);
        int command;
        while ((command = scanner.nextInt()) != 2) {
            System.out.println("command id: " + command);
            if (command == 1) {
//                crptApi.createDocumentForProducedProduct();
            } else {
                System.out.println("Wrong command");
            }
        }
        System.out.println("Thanks for using app.");
    }
}
