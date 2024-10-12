package com.myapp;

import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.Label;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.Image;


import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class CarDetection {

    private static final String S3_BUCKET_URL = "https://njit-cs-643.s3.us-east-1.amazonaws.com/";
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/098150390570/CarDetectionQueue.fifo";
						// "https://sqs.us-east-1.amazonaws.com/098150390570/CarDetectionQueue";

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

        // Process 10 images
        for (int i = 1; i <= 10; i++) {
            String imageName = i + ".jpg";
            String imageUrl = S3_BUCKET_URL + imageName;
            downloadAndDetectCars(imageUrl, imageName);

	    // delay of 2 seconds (2000 milliseconds)
	    try {
	        Thread.sleep(2000); // Adjust the delay as needed
	    } catch (InterruptedException e) {
	        Thread.currentThread().interrupt(); // Restore the interrupted status
	        System.out.println("Sleep interrupted");
	    }

        }

        // Send termination signal (-1) to SQS to notify Instance B
        sendMessageToSQS("-1");
    }

    // Method to download an image and check for cars
    private static void downloadAndDetectCars(String imageUrl, String imageName) {
        try {
            // Download the image to a temporary location
            InputStream in = new URL(imageUrl).openStream();
            Files.copy(in, Paths.get("/tmp/" + imageName), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Downloaded image: " + imageUrl);

            // Perform car detection using Rekognition
            detectCarsInImage("/tmp/" + imageName, imageName);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to detect cars in the downloaded image using Rekognition
    private static void detectCarsInImage(String imagePath, String imageName) {
        try {
            // Create an Image object with bytes
//            Image image = Image.builder()
  //                  .bytes(SdkBytes.fromFile(Paths.get(imagePath))) // Updated method
    //                .build();

// // //
        // Read the file into bytes
 	    byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));

        // Create an Image object with bytes
	    Image image = Image.builder()
                .bytes(SdkBytes.fromByteArray(imageBytes))  // Use fromByteArray instead of fromFile
                .build();

// // //
            DetectLabelsRequest request = DetectLabelsRequest.builder()
                    .image(image)
                    .maxLabels(10)
                    .minConfidence(90F)
                    .build();

            DetectLabelsResponse result = rekognitionClient.detectLabels(request);
            List<Label> labels = result.labels();

            // Check if "Car" is one of the detected labels with confidence > 90%
            for (Label label : labels) {
                if (label.name().equalsIgnoreCase("Car") && label.confidence() > 90) {
                    System.out.println("Car detected in image: " + imageName);
                    // Send image index to SQS if a car is detected
                    sendMessageToSQS(imageName);
                    return;
                }
            }
            System.out.println("No car detected in image: " + imageName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to send a message (image index or termination signal) to SQS
    private static void sendMessageToSQS(String message) {
        try {
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(SQS_QUEUE_URL)
                    .messageBody(message)
                    .messageGroupId("carDetectionGroup") // Required for FIFO queue
                    .messageDeduplicationId(message)     // Optional: If content-based deduplication is not enabled, ensure deduplication
                    .build();
            sqsClient.sendMessage(sendMsgRequest);
            System.out.println("Sent message to SQS: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

