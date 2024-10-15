package com.myapp;

import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectTextRequest;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.TextDetection;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.rekognition.model.Image;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CarTextDetection {

    private static final String S3_BUCKET_URL = "https://njit-cs-643.s3.us-east-1.amazonaws.com/";
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/098150390570/CarDetectionQueue.fifo";
    private static final String TEMP_DIR = "/tmp/";
    private static final String OUTPUT_DIR = "/home/ec2-user/as1/image-recognition-pipeline/textdetection/";
    
    private static List<String> detectedTexts = new ArrayList<>();
    
    private static RekognitionClient rekognitionClient;
    private static SqsClient sqsClient;

    public static void main(String[] args) {
        // Initialize Rekognition and SQS clients
        rekognitionClient = RekognitionClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        // Continuous processing loop
        while (true) {
            // Delete all temporary files before processing new messages
            deleteTemporaryFiles();

            // Process messages from SQS
            processSQSMessages();

            // Inform user about waiting for new messages
            System.out.println("Waiting for new messages...");
            try {
                Thread.sleep(5000); // Wait for a short period before checking again
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                System.out.println("Processing interrupted.");
                break;
            }
        }
    }

    // Method to delete all temporary files
    private static void deleteTemporaryFiles() {
        try {
            Files.list(Paths.get(TEMP_DIR))
                    .filter(Files::isRegularFile) // Only delete regular files
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                            System.out.println("Deleted temporary file: " + file.getFileName());
                        } catch (IOException e) {
                            System.err.println("Failed to delete file: " + file.getFileName());
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error reading temporary directory: " + TEMP_DIR);
            e.printStackTrace();
        }
    }


/*
    // Method to process messages from the SQS queue
    private static void processSQSMessages() {
        List<String> detectedTexts = new ArrayList<>();

        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(SQS_QUEUE_URL)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(5)
                .build();

        List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

        if (messages.isEmpty()) {
            return; // No messages to process, return to check for new messages
        }

        for (Message message : messages) {
            String imageName = message.body();

	    System.out.println(" >>>>>>>>>>>>>>>>>>>>>>>>>> Received message: " + message.body());

            // If the message is the termination signal (-1), stop processing
            if (imageName.equals("-1")) {
                System.out.println("Received termination signal. Stopping processing.");
                writeDetectedTextsToFile(detectedTexts);
                deleteMessageFromSQS(message.receiptHandle()); // Delete termination signal immediately
                System.exit(0);
		// return;
            }

            // Download and process the image for text detection
            String detectedText = downloadAndDetectText(imageName);
            if (detectedText != null) {
                detectedTexts.add(detectedText);
            }
	    
	    System.out.println("\n\n +++++++ updated Detected Texts: " + detectedTexts + "\n++++++++++++++++\n");

            // Delete the message from the queue after processing
            deleteMessageFromSQS(message.receiptHandle());
        }
    }
*/

// Method to process messages from the SQS queue
private static void processSQSMessages() {
   // List<String> detectedTexts = new ArrayList<>();  // This list will accumulate texts from all messages

    // Receive messages from the SQS queue
    ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
            .queueUrl(SQS_QUEUE_URL)
            .maxNumberOfMessages(10)
            .waitTimeSeconds(5)
            .build();

    // Fetch messages from SQS
    List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

    // If there are no messages, return to check for new messages later
    if (messages.isEmpty()) {
        return;
    }

    // Loop through each message
    for (Message message : messages) {
        String imageName = message.body();

        System.out.println(" >>>>>>>>>>>>>>>>>>>>>>>>>> Received message: " + message.body());

        // If the message is the termination signal (-1), stop processing
        if (imageName.equals("-1")) {
            System.out.println("Received termination signal. Stopping processing.\n");
            
            // Write accumulated detected texts to a file
            writeDetectedTextsToFile(detectedTexts);
            
            // Delete the termination signal message from the queue
            deleteMessageFromSQS(message.receiptHandle());
            
            // Exit the system after processing the termination signal
            System.exit(0);
        }

        // Download and process the image to detect text
        String detectedText = downloadAndDetectText(imageName);

        // If text was detected, add it to the list of detected texts
        if (detectedText != null) {
            detectedTexts.add(detectedText); // Append new detected text to the list
        }

        // Delete the message from the queue after processing
        deleteMessageFromSQS(message.receiptHandle());
    }
}

//  new ends here

    // Method to download an image and detect text using Rekognition
    private static String downloadAndDetectText(String imageName) {
        try {
            String imageUrl = S3_BUCKET_URL + imageName;
            InputStream in = new URL(imageUrl).openStream();
            Files.copy(in, Paths.get(TEMP_DIR + imageName));

            System.out.println("Downloaded image: " + imageUrl);

            // Perform text detection using Rekognition
            return detectTextInImage(TEMP_DIR + imageName, imageName);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; // Return null if there was an error
    }

    // Method to detect text in the downloaded image using Rekognition

	private static String detectTextInImage(String imagePath, String imageName) {
	    StringBuilder detectedText = new StringBuilder();
	    try {
	        // Read the file into bytes
	        byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));

	        // Create an Image object with bytes
	        Image image = Image.builder()
	                .bytes(SdkBytes.fromByteArray(imageBytes))
	                .build();

	        DetectTextRequest request = DetectTextRequest.builder()
	                .image(image)
	                .build();

	        DetectTextResponse result = rekognitionClient.detectText(request);
	        List<TextDetection> textDetections = result.textDetections();

	        System.out.println("Detected text in image " + imageName + ":");

	        // Remove ".jpg" from the image name
	        String imageIndex = imageName.replace(".jpg", "");

	        detectedText.append(imageIndex).append(": "); // Add image index first without ".jpg"
	        for (TextDetection text : textDetections) {
	            detectedText.append(text.detectedText()).append(" "); // Append detected text only
	        }
	        return detectedText.toString().trim(); // Return detected text as a single string

	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return null; // Return null if there was an error
	}


/*
    private static String detectTextInImage(String imagePath, String imageName) {
        StringBuilder detectedText = new StringBuilder();
        try {
            // Read the file into bytes
            byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));

            // Create an Image object with bytes
            Image image = Image.builder()
                    .bytes(SdkBytes.fromByteArray(imageBytes))
                    .build();

            DetectTextRequest request = DetectTextRequest.builder()
                    .image(image)
                    .build();

            DetectTextResponse result = rekognitionClient.detectText(request);
            List<TextDetection> textDetections = result.textDetections();

            System.out.println("Detected text in image " + imageName + ":");
            for (TextDetection text : textDetections) {
                detectedText.append(text.detectedText()).append(" (Confidence: ").append(text.confidence()).append(")\n");
                System.out.println(text.detectedText() + " (Confidence: " + text.confidence() + ")");
            }
            return detectedText.toString().trim(); // Return detected text as a single string

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Return null if there was an error
    }

*/

    // Method to delete a processed message from SQS
    private static void deleteMessageFromSQS(String receiptHandle) {
        try {
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(SQS_QUEUE_URL)
                    .receiptHandle(receiptHandle)
                    .build();
            sqsClient.deleteMessage(deleteMessageRequest);
            System.out.println("Deleted message from SQS...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to write detected texts to a file after receiving termination signal
    private static void writeDetectedTextsToFile(List<String> detectedTexts) {
        String timestamp = Instant.now().toString().replace(":", "-").replace(".", "-"); // Format timestamp
        String outputFilePath = OUTPUT_DIR + "results_" + timestamp + ".txt";

        try {
            Files.write(Paths.get(outputFilePath), detectedTexts, StandardOpenOption.CREATE);
	    System.out.println("\n************************** \n\n Wrote detected texts to file: " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Failed to write detected texts to file.");
            e.printStackTrace();
        }
    }
}

