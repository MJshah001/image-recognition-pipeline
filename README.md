# Image Recognition Pipeline with AWS EC2, S3, SQS, and Rekognition

## Project Overview

This project, as shown in the architecture diagram, involves two EC2 instances working in parallel and communicating through SQS (Simple Queue Service) to perform image recognition tasks using AWS Rekognition.

![Architecture](https://github.com/MJshah001/image-recognition-pipeline/blob/main/Architecture%20diagram.jpg)

### EC2 Instance A:
- Retrieves images from the S3 bucket.
- Performs car detection using AWS Rekognition.
- If a car is detected with confidence > 90%, the image index is pushed to SQS.

### EC2 Instance B:
- Retrieves image indexes from SQS.
- Downloads the corresponding images from S3.
- Uses AWS Rekognition to perform text recognition on those images.
- Once all the images are processed, output the results (indexes and text) to a file.

---

## Setup and Configuration

### Step 1: Log in to AWS Management Console
1. Go to the AWS Management Console.
2. Sign in with your credentials.

### Step 2: Configure a Security Group (`EC2-Instance-Security-Group`)
Ensure that the security group allows:
- SSH, HTTP & HTTPS access only from your IP to manage and access the EC2 instances remotely.

Download the `.pem` file.

### Step 3: Create Two EC2 Instances (EC2 A & EC2 B)
1. Choose **Amazon Machine Image (AMI):** Amazon Linux 2 AMI.
2. Choose an **Instance Type:** `t2.micro`.
3. Add Storage: Keep the default 8 GB (gp2) EBS volume.
4. Configure the Security Group: Use the `EC2-Instance-Security-Group` created in Step 2.

### Step 4: SSH into Both EC2 Instances
Open separate terminal windows to SSH into both instances:

```bash
ssh -i /path/to/your-key.pem ec2-user@your-ec2-a-public-ip
```

If you face permission issues, try this:

```bash
chmod 400 my-ec2-keypair.pem
```

### Step 5:  **Install AWS CLI** (Both Instances)

```bash
sudo yum install aws-cli -y
```

**Configure AWS CLI**:

```bash
aws configure
```

Provide your AWS credentials:
- **AWS Access Key ID**
- **AWS Secret Access Key**
- **Default region name**: `us-east-1` (or your assigned region)
- **Default output format**: `json`

If using AWS Educate, add the session token:
```bash
nano ~/.aws/credentials
```
Enter following:
```bash
[default]
aws_access_key_id = <Your_Access_Key_ID>
aws_secret_access_key = <Your_Secret_Access_Key>
aws_session_token = <Your_Session_Token>
```

Verify Setup:
```
nano ~/.aws/credentials
```

### Step 6: **Install Java** (Both Instances):

Update packages:
```
sudo yum update -y
```

Install Java Development Kit (JDK):
```
sudo amazon-linux-extras install java-openjdk11 -y
```

Verify Java installation:

```
java -version
```

### Step 7: **Install Maven** (Both Instances)

```bash
sudo yum install maven -y
```

### **Note :** You have 2 options : either clone this github repository (follow step 8,9 & 10 ) or create from scratch (follow step 11 onwards)


### Step 8: Using This GitHub Repository (Both Instances)

**Install Git**:

```bash
sudo yum install git -y
```

**Clone the Repository**:

```bash
git clone https://github.com/MJshah001/image-recognition-pipeline.git
```
```
cd image-recognition-pipeline
```

### Step 9: Configure EC2 A

**Navigate to the `cardetection` Directory**:

```bash
cd cardetection
```

**Modify the Java Code with Your SQS Queue URL**:

```bash
nano src/main/java/com/myapp/CarDetection.java
```

Replace the SQS URL on line 29 with your SQS queue:

```java
private static final String SQS_QUEUE_URL = "https://sqs.<your-region>.amazonaws.com/<your-account-id>/CarDetectionQueue.fifo";
```

#### *Note* : make sure it's a fifo queue with default settings

**Compile the Code**:

```bash
mvn clean install
```

**Run the Java Program**:

```bash
mvn exec:java
```

### Step 10: Configure EC2 B

**Navigate to the `textdetection` Directory**:

```bash
cd textdetection
```

**Modify the Java Code with Your SQS Queue URL**:

```bash
nano src/main/java/com/myapp/CarTextDetection.java
```

Replace the SQS URL on line 30 with your SQS queue:

```java
private static final String SQS_QUEUE_URL = "https://sqs.<your-region>.amazonaws.com/<your-account-id>/CarDetectionQueue.fifo";
```

#### *Note* : make sure it's a fifo queue with default settings

**Compile the Code**:

```bash
mvn clean install
```

**Run the Java Program**:

```bash
mvn exec:java
```

**View the Output**:

After processing, you'll see a `results_{TIMESTAMP}.txt` file. Use the following command to view it:

```
ls
```
```bash
cat results_{TIMESTAMP}.txt
```

Replace `{TIMESTAMP}` with the actual timestamp of the generated file.


### Step 11: Create the Project from Scratch (On Both Instances)

If you prefer to create the project manually instead of cloning the repository, follow these steps.

Create a project directory
```bash
mkdir ~/image-recognition-pipeline
```
move into the directory
```
cd ~/ image-recognition-pipeline
```

### Step 12: Set up EC2 A

**Create a Maven Project**:

```bash
mvn archetype:generate -DgroupId=com.myapp -DartifactId=cardetection -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
```

**Navigate to the Project Directory**:

```bash
cd cardetection
```

**Add AWS SDK Dependencies to `pom.xml`**:

Open the `pom.xml` file:

```bash
nano pom.xml
```

Copy and paste the relevant AWS SDK dependencies from the [GitHub repository](https://github.com/MJshah001/image-recognition-pipeline) (located in `cardetection/pom.xml`).

**Create `CarDetection.java`**:

In the `src/main/java/com/myapp` directory, create the `CarDetection.java` file:

```bash
nano src/main/java/com/myapp/CarDetection.java
```

Copy and paste the contents from the `CarDetection.java` file in the GitHub repository.

**Modify the Java Code**:

Replace the SQS URL in `CarDetection.java`:

```java
private static final String SQS_QUEUE_URL = "https://sqs.<your-region>.amazonaws.com/<your-account-id>/CarDetectionQueue.fifo";
```

**Compile the Code**:

```bash
mvn clean install
```

**Run the Program**:

```bash
mvn exec:java
```

### Step 13: Set up EC2 B

**Create a Maven Project**:

```bash
mvn archetype:generate -DgroupId=com.myapp -DartifactId=textdetection -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
```

**Navigate to the Project Directory**:

```bash
cd textdetection
```

**Add AWS SDK Dependencies to `pom.xml`**:

Open the `pom.xml` file:

```bash
nano pom.xml
```

Copy and paste the relevant AWS SDK dependencies from the [GitHub repository](https://github.com/MJshah001/image-recognition-pipeline) (located in `textdetection/pom.xml`).


**Create `CarTextDetection.java`**:

In the `src/main/java/com/myapp` directory, create the `CarTextDetection.java` file:

```bash
nano src/main/java/com/myapp/CarTextDetection.java
```

Copy and paste the contents from the `CarTextDetection.java` file in the GitHub repository.

**Modify the Java Code**:

Replace the SQS URL in `CarTextDetection.java`:

```java
private static final String SQS_QUEUE_URL = "https://sqs.<your-region>.amazonaws.com/<your-account-id>/CarDetectionQueue.fifo";
```

**Compile the Code**:

```bash
mvn clean install
```

**Run the Program**:

```bash
mvn exec:java
```

**View the Output**:

After processing, you'll see a `results_{TIMESTAMP}.txt` file. Use the following command to view it:

```
ls
```
```bash
cat results_{TIMESTAMP}.txt
```

Replace `{TIMESTAMP}` with the actual timestamp of the generated file.

---

Once both EC2 instances are up and running, they will work together in parallel as follows:

- **EC2 A** will detect cars in images and push results to SQS.
- **EC2 B** will process the SQS messages, detect text in images, and output the results to a text file.



