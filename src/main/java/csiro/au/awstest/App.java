package csiro.au.awstest;

import java.util.List;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.util.Base64;
import com.amazonaws.util.StringInputStream;

/**
 * Hello world!
 *
 */
public class App {
	private static final String ROLE_ARN = "arn:aws:iam::696640869989:role/xacc_test";
	private static final String BUCKER_NAME = "frefgrefesf";

	private static AWSCredentials AwsCredentials;

	private static void init() throws Exception {
		AwsCredentials = new PropertiesCredentials(App.class.getResourceAsStream("/AwsCredentials.properties"));
	}

	public static void main(String[] args) throws Exception {
		init();
		// testStsS3();
		testStsEc2();
	}

	public static void testStsEc2() throws Exception {
		// Step 1. Use Joe.s long-term credentials to call the
		// AWS Security Token Service (STS) AssumeRole API, specifying
		// the ARN for the role DynamoDB-RO-role in research@example.com.

		AWSSecurityTokenServiceClient stsClient = new AWSSecurityTokenServiceClient(AwsCredentials);

		AssumeRoleRequest assumeRequest = new AssumeRoleRequest().withRoleArn(ROLE_ARN).withDurationSeconds(3600)
				.withExternalId("1234").withRoleSessionName("demo");

		AssumeRoleResult assumeResult = stsClient.assumeRole(assumeRequest);

		// Step 2. AssumeRole returns temporary security credentials for
		// the IAM role.

		BasicSessionCredentials temporaryCredentials = new BasicSessionCredentials(
				assumeResult.getCredentials().getAccessKeyId(), assumeResult.getCredentials().getSecretAccessKey(),
				assumeResult.getCredentials().getSessionToken());

		AmazonEC2 ec2 = new AmazonEC2Client(temporaryCredentials);
		ec2.setEndpoint("ec2.ap-southeast-2.amazonaws.com");

		// CREATE EC2 INSTANCES
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest().withInstanceType("m3.medium")
				.withImageId("ami-0487de67").withMinCount(1).withMaxCount(1).withInstanceInitiatedShutdownBehavior("terminate");

		RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);

		// TAG EC2 INSTANCES
		List<Instance> instances = runInstances.getReservation().getInstances();
		int idx = 1;
		for (Instance instance : instances) {
			CreateTagsRequest createTagsRequest = new CreateTagsRequest();
			createTagsRequest.withResources(instance.getInstanceId()) //
					.withTags(new Tag("Name", "ANVGL Job: " + idx));
			ec2.createTags(createTagsRequest);

			idx++;
		}
	}

	public static void testStsS3() throws Exception {
		// init();

		// Step 1. Use Joe.s long-term credentials to call the
		// AWS Security Token Service (STS) AssumeRole API, specifying
		// the ARN for the role DynamoDB-RO-role in research@example.com.

		AWSSecurityTokenServiceClient stsClient = new AWSSecurityTokenServiceClient(AwsCredentials);

		AssumeRoleRequest assumeRequest = new AssumeRoleRequest().withRoleArn(ROLE_ARN).withDurationSeconds(3600)
				.withExternalId("1234").withRoleSessionName("demo");

		AssumeRoleResult assumeResult = stsClient.assumeRole(assumeRequest);

		// Step 2. AssumeRole returns temporary security credentials for
		// the IAM role.

		BasicSessionCredentials temporaryCredentials = new BasicSessionCredentials(
				assumeResult.getCredentials().getAccessKeyId(), assumeResult.getCredentials().getSecretAccessKey(),
				assumeResult.getCredentials().getSessionToken());

		// Step 3. Make DynamoDB service calls to read data from a
		// DynamoDB table, stored in research@example.com, using the
		// temporary security credentials from the DynamoDB-ReadOnly-role
		// that were returned in the previous step.
		AmazonS3 s3client = new AmazonS3Client(temporaryCredentials);
		s3client.putObject(BUCKER_NAME, "test2.txt", new StringInputStream("This is a test 2"), new ObjectMetadata());
	}
}
