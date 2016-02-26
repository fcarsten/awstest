package csiro.au.awstest;

import java.util.List;
import java.util.Properties;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.domain.Credentials;
import org.jclouds.sts.STSApi;
import org.jclouds.sts.domain.UserAndSessionCredentials;
import org.jclouds.sts.options.AssumeRoleOptions;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
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
import com.amazonaws.util.StringInputStream;
import com.google.common.base.Supplier;

/**
 * AWS Cross Account Access test
 * 
 * This code is based on the article:
 * https://blogs.aws.amazon.com/security/post/TxC24FI9IDXTY1/Delegating-API-Access-to-AWS-Services-Using-IAM-Roles
 */
public class App {
	
	//
	// Change the below to match your case.
	//

	private static final String STS_ROLE_ARN = "arn:aws:iam::696640869989:role/vbkd-dsadss-AnvglStsRole-1QZG62NWIOK2";
	private static final String S3_PROFILE_ARN = "arn:aws:iam::696640869989:instance-profile/vbkd-dsadss-AnvglS3InstanceProfile-17Z06U2BEOANC";

	//private static final String BUCKER_NAME = "job-MORPH-BT-carsten_friedrich_gmail_com-0000000024/vl-download.sh";
	private static final String BUCKER_NAME = "anvgl-csiro"; // Must match value in policy
	private static final String AMI_NAME = "ami-0487de67"; // Must match value in policy

	private static final String CLIENT_SECRET = "1234"; // Must match value in policy
	private static final String AWS_EC2_ENDPOINT = "ec2.ap-southeast-2.amazonaws.com";
	
	//
	// Probably don't need to change anything below
	//

	private static AWSCredentials AwsCredentials;

	private static void init() throws Exception {
		AwsCredentials = new PropertiesCredentials(App.class.getResourceAsStream("/AwsCredentials.properties"));
	}

	public static void main(String[] args) throws Exception {
		init();
		testStsS3JClouds();
//		testStsS3();
//		testStsEc2();
	}

	public static void testStsEc2() throws Exception {
		// Step 1. Use Joe.s long-term credentials to call the
		// AWS Security Token Service (STS) AssumeRole API, specifying
		// the ARN for the role DynamoDB-RO-role in research@example.com.

		AWSSecurityTokenServiceClient stsClient = new AWSSecurityTokenServiceClient(AwsCredentials);

		AssumeRoleRequest assumeRequest = new AssumeRoleRequest().withRoleArn(STS_ROLE_ARN).withDurationSeconds(3600)
				.withExternalId(CLIENT_SECRET).withRoleSessionName("demo");

		AssumeRoleResult assumeResult = stsClient.assumeRole(assumeRequest);

		// Step 2. AssumeRole returns temporary security credentials for
		// the IAM role.

		BasicSessionCredentials temporaryCredentials = new BasicSessionCredentials(
				assumeResult.getCredentials().getAccessKeyId(), assumeResult.getCredentials().getSecretAccessKey(),
				assumeResult.getCredentials().getSessionToken());

		AmazonEC2 ec2 = new AmazonEC2Client(temporaryCredentials);
		ec2.setEndpoint(AWS_EC2_ENDPOINT);

		IamInstanceProfileSpecification iamInstanceProfile = new IamInstanceProfileSpecification().withArn(S3_PROFILE_ARN);
		// CREATE EC2 INSTANCES
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest().withInstanceType("m3.medium").withIamInstanceProfile(iamInstanceProfile)
				.withImageId(AMI_NAME).withMinCount(1).withMaxCount(1).withInstanceInitiatedShutdownBehavior("terminate");

		RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);

		// TAG EC2 INSTANCES
		List<Instance> instances = runInstances.getReservation().getInstances();
		int idx = 1;
		for (Instance instance : instances) {
			CreateTagsRequest createTagsRequest = new CreateTagsRequest();
			createTagsRequest.withResources(instance.getInstanceId()) //
					.withTags(new Tag("Name", "XACCESS test- : " + idx));
			ec2.createTags(createTagsRequest);

			idx++;
		}
	}

	public static void testStsS3JClouds() throws Exception {
        String regionName = "ap-southeast-2";
        boolean relaxHostName = false;
        boolean stripExpectHeader = true;
		String endpoint=null;

        Properties properties = new Properties();
		properties.setProperty("jclouds.relax-hostname", relaxHostName ? "true" : "false");
		properties.setProperty("jclouds.strip-expect-header", stripExpectHeader ? "true" : "false");

		if (regionName  != null) {
            properties.setProperty("jclouds.region", regionName);
        }
		
		STSApi api = ContextBuilder.newBuilder("sts")
				.credentials(AwsCredentials.getAWSAccessKeyId(), AwsCredentials.getAWSSecretKey())
				.buildApi(STSApi.class);

		AssumeRoleOptions assumeRoleOptions = new AssumeRoleOptions().durationSeconds(3600).externalId(CLIENT_SECRET);
		final UserAndSessionCredentials credentials = api.assumeRole(STS_ROLE_ARN, "demo", assumeRoleOptions);

		Supplier<Credentials> credentialsSupplier = new Supplier<Credentials>() {
		    @Override
		    public Credentials get() {
		        return credentials.getCredentials();
		    }
		};
		
        ContextBuilder builder2 = ContextBuilder.newBuilder("aws-s3").overrides(properties).credentialsSupplier(credentialsSupplier);
        
		if (endpoint != null) {
			builder2.endpoint(endpoint);
		}

		BlobStoreContext context =  builder2.buildView(BlobStoreContext.class);

        BlobStore bs = context.getBlobStore();

        Blob newBlob = bs.blobBuilder("job-MORPH-BT-carsten_friedrich_gmail_com-0000000024/vl-download.sh")
                .payload("This is a test for jclouds blob".getBytes("Utf-8"))
                .build();

        bs.putBlob(BUCKER_NAME, newBlob);

	}
	
	public static void testStsS3() throws Exception {
		// Step 1. Use Joe.s long-term credentials to call the
		// AWS Security Token Service (STS) AssumeRole API, specifying
		// the ARN for the role DynamoDB-RO-role in research@example.com.

		AWSSecurityTokenServiceClient stsClient = new AWSSecurityTokenServiceClient(AwsCredentials);

		AssumeRoleRequest assumeRequest = new AssumeRoleRequest().withRoleArn(STS_ROLE_ARN).withDurationSeconds(3600)
				.withExternalId(CLIENT_SECRET).withRoleSessionName("demo");

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
		s3client.createBucket(BUCKER_NAME);
		s3client.putObject(BUCKER_NAME, "test2.txt", new StringInputStream("This is a test 2"), new ObjectMetadata());
	}
}
