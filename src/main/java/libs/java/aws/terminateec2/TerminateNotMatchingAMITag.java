package libs.java.aws.terminateec2;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

/**
 * A simple service to terminate instances according to given tag value. An
 * instance will be terminated if - it does not belong to owner (self) created
 * AMI, it does not contain mentioned TAG in AMI, the tag does not contain the
 * correct value as mentioned. Take caution to run this code, as this will
 * terminate all the instances which are not meeting criteria.
 * 
 * 
 * As you see, this has nothing to do with AWS lambda - but we can leverage this
 * code to run every few minutes to remove invalid instance in our account. We
 * can use AWS services to do this - for this example, we will use cloud watch
 * to schedule lambda method.
 * 
 * Also because it is a simple example, all code is placed in this simple
 * service. Refer document for setting up cloud watch and lambda method
 * 
 * @author Kuldeep
 *
 */

public class TerminateNotMatchingAMITag {

	private String TAG_VALUE = "WHITELIST";
	private String TAG = "FLAG";
	static Properties p = new Properties();
	static {
		try {
			// load accoutn properties configuration from classpath
			p.load(TerminateNotMatchingAMITag.class.getResourceAsStream("/config.properties"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		new TerminateNotMatchingAMITag().terminateInvalidTagInstances();
	}

	public void terminateInvalidTagInstances() throws Exception {
		BasicAWSCredentials creds = new BasicAWSCredentials(p.getProperty("accessKey"), p.getProperty("secretKey"));
		AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(creds))
				.build();
		boolean done = false;

		DescribeImagesRequest imgRequest = new DescribeImagesRequest();
		// only ami created by self, this search takes alot of time, you should
		// filter only selected ami
		// demo - expect all ami will be returned in single request

		DescribeImagesResult imgResponse = ec2.describeImages(imgRequest.withOwners("self"));
		List<String> toTerminate = new ArrayList<>();
		while (!done) {
			DescribeInstancesRequest request = new DescribeInstancesRequest();
			DescribeInstancesResult response = ec2.describeInstances(request);

			for (Reservation reservation : response.getReservations()) {

				for (Instance instance : reservation.getInstances()) {

					if (instance.getState().getCode() != 16) {
						// skip non-running instances
						continue;
					}
					// only running instances

					Image image = getImage(instance, imgResponse.getImages());

					if (image == null) { // instance is not from owner images,
											// add to terminate
						toTerminate.add(instance.getInstanceId());
						continue;
					}
					Tag tag = findAndReturnTag(image, TAG);

					if (tag == null) {
						// does not contain tag, add to terminate
						toTerminate.add(instance.getInstanceId());
						continue;
					}
					if (!hasTagValue(tag)) {
						// Tag value is incorrect, add to terminate
						toTerminate.add(instance.getInstanceId());
						continue;

					}
				}
			}
			request.setNextToken(response.getNextToken());

			if (response.getNextToken() == null) {
				done = true;
			}
		}
		System.out.println("Terminating instances " + toTerminate);
		TerminateInstancesRequest request = new TerminateInstancesRequest(toTerminate);
		
		System.out.println(ec2.terminateInstances(request));
		System.out.println("End");
	}

	private Image getImage(Instance instance, List<Image> images) {
		for (Image img : images) {
			if (instance.getImageId().equalsIgnoreCase(img.getImageId())) {
				return img;
			}
		}
		return null;

	}

	private Tag findAndReturnTag(Image image, String tagKey) {
		if (image == null || image.getTags() == null) {
			return null;
		}
		for (Tag t : image.getTags()) {
			if (t.getKey().equalsIgnoreCase(tagKey)) {
				return t;
			}
		}
		return null;
	}

	private boolean hasTagValue(Tag t) {
		if (t == null || t.getValue() == null) {
			return false;
		}
		if (t.getValue().equalsIgnoreCase(TAG_VALUE)) {
			return true;
		}
		return false;
	}
}
