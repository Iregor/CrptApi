package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CrptApiBlockingQueue {
    private final int requestLimit;
    private final TimeUnit lifeCycle;
    private final ArrayBlockingQueue<LocalDateTime> requestQueue;
    private final ObjectMapper mapper;
    private final String HOST = "https://ismp.crpt.ru";
    private final Base64.Encoder base64Encoder;
    private final HttpClient client;

    public CrptApiBlockingQueue(TimeUnit lifeCycle, int requestLimit) {
        this.lifeCycle = lifeCycle;
        this.requestLimit = requestLimit;
        mapper = new ObjectMapper();
        base64Encoder = Base64.getEncoder();
        requestQueue = new ArrayBlockingQueue<>(requestLimit);
        client = HttpClient.newHttpClient();
        QueueConsumer consumer = new QueueConsumer();
        Thread consumerThread = new Thread(consumer::consumeQueue);
        consumerThread.start();
    }

    public String createProducedProductDocument(Object productDocument,
                                              String signature,
                                              ProductGroup productGroup,
                                              DocumentFormat documentFormat,
                                              String token) throws InterruptedException, IOException {
        validateProducedProductDocumentData(productDocument, signature, productGroup, documentFormat, token);
        putRequestTimeInQueue();
        return sendProducedProductDocumentRequest(documentFormat, productDocument, productGroup, signature, token);
    }

    private void putRequestTimeInQueue() throws InterruptedException {
        try {
            //we have blocking here if queue is full
            //wait for QueueConsumer to free some space
            requestQueue.put(LocalDateTime.now());
        } catch (InterruptedException exc) {
            throw new InterruptedException("Thread was interrupted while waiting for vacant queue place.");
        }
    }

    private String sendProducedProductDocumentRequest(DocumentFormat documentFormat,
                                                    Object productDocument,
                                                    ProductGroup productGroup,
                                                    String signature,
                                                    String token) throws InterruptedException, IOException {
        String path = "/api/v3/lk/documents/commissioning/contract/create";
        String requestBody = getProducedProductDocumentRequestBody(documentFormat, productDocument, productGroup, signature);
        try {
            return sendRequest(path, token, requestBody);
        } catch (InterruptedException exc) {
            throw new InterruptedException("Thread was interrupted while waiting server response.");
        }
    }


    private String sendRequest(String path, String token, String requestBody) throws InterruptedException, IOException {
        String uri = HOST + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("content-type", "application-json")
                .header("Authorization", "Bearer " + token)
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return parseHttpResponse(response);
    }

    private String parseHttpResponse(HttpResponse<String> response) throws JsonProcessingException {
        int statusCode = response.statusCode();
        if (statusCode / 100 == 2) {
            return mapper.reader().readTree(response.body()).get("value").asText();
        } else {
            throw new RuntimeException(String.format("Response code not 2XX. Actual: %d.", statusCode));
        }
    }

    private String getProducedProductDocumentRequestBody(DocumentFormat documentFormat,
                                                         Object productDocument,
                                                         ProductGroup productGroup,
                                                         String signature) throws IOException {
        String productDocumentBase64 = parseDocumentToBase64(documentFormat, productDocument);
        String signatureBase64 = base64Encoder.encodeToString(signature.getBytes(StandardCharsets.UTF_8));
        String documentType = getDocumentType(documentFormat).name();

        ProducedProductDocumentRequestBody requestBody = ProducedProductDocumentRequestBody
                .builder()
                .documentFormat(documentFormat.name())
                .productDocument(productDocumentBase64)
                .productGroup(productGroup.name())
                .signature(signatureBase64)
                .type(documentType)
                .build();
        return mapper.writer().withDefaultPrettyPrinter().writeValueAsString(requestBody);
    }

    private String parseDocumentToBase64(DocumentFormat documentFormat, Object productDocument) throws IOException {
        String parsedDocument = switch (documentFormat) {
            case MANUAL -> parseDocumentToJson(productDocument);
            case XML -> parseDocumentToXML(productDocument);
            case CSV -> parseDocumentToCSV(productDocument);
        };
        return base64Encoder.encodeToString(parsedDocument.getBytes(StandardCharsets.UTF_8));
    }

    private Type getDocumentType(DocumentFormat documentFormat) {
        return switch (documentFormat) {
            case MANUAL -> Type.LP_INTRODUCE_GOODS;
            case XML -> Type.LP_INTRODUCE_GOODS_XML;
            case CSV -> Type.LP_INTRODUCE_GOODS_CSV;
        };
    }

    private String parseDocumentToJson(Object productDocument) throws IOException {
        ObjectWriter jsonWriter = mapper.writer().withDefaultPrettyPrinter();
        return jsonWriter.writeValueAsString(productDocument);
    }

    private String parseDocumentToCSV(Object productDocument) {
        return null;
        //for possible extension
    }

    private String parseDocumentToXML(Object productDocument) {
        return null;
        //for possible extension
    }

    private void validateProducedProductDocumentData(Object productDocument, String signature, ProductGroup productGroup, DocumentFormat documentFormat, String token) {
        if (productDocument == null || signature == null || productGroup == null || documentFormat == null || token == null) {
            throw new RuntimeException("Produced product document creating: null arguments are not allowed.");
        }
    }

    private class QueueConsumer {
        //class to consume request queue according to specified time unit
        private void consumeQueue() {
            while (!Thread.interrupted()) {
                LocalDateTime instantToConsume = requestQueue.peek();
                if (instantToConsume == null) {
                    //queue is empty, guarantee nothing to consume at least lifeCycle
                    try {
                        Thread.sleep(lifeCycle.toMillis(1));
                    } catch (InterruptedException exc) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }

                LocalDateTime consumeBefore = LocalDateTime.now().minusNanos(lifeCycle.toNanos(1));
                if (instantToConsume.isBefore(consumeBefore)) {
                    //request is ready to consume, lets poll it
                    requestQueue.poll();
                } else {
                    Duration waitForConsuming = Duration.between(consumeBefore, instantToConsume);
                    try {
                        //wait exactly as needed to consume the closest request
                        Thread.sleep(waitForConsuming.toMillis() + 1);
                    } catch (InterruptedException exc) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            System.out.println("Consumer was interrupted and terminated.");
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    private static class ProducedProductDocumentRequestBody {
        private String documentFormat;
        private String productDocument;
        private String productGroup;
        private String signature;
        private String type;
    }

    private enum DocumentFormat {
        MANUAL,
        XML,
        CSV
    }

    private enum ProductGroup {
        clothes,
        shoes,
        tobacco,
        perfumery,
        tires,
        electronics,
        pharma,
        milk,
        bicycle,
        wheelchairs
    }

    private enum Type {
        LP_INTRODUCE_GOODS,
        LP_INTRODUCE_GOODS_CSV,
        LP_INTRODUCE_GOODS_XML
    }
}