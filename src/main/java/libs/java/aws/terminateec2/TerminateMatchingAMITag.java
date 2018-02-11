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
 * See {@link TerminateNotMatchingAMITag} This does opposite to tag matching,
 * simple use case you created instances with AMI tag of version-01, on your new
 * rollout you want to use new AMIs and remove all old AMI running instance.
 * 
 * 
 * 
 * @author Kuldeep
 *
 */

public class TerminateMatchingAMITag {

	private String TAG_VALUE = "VERSION-01";
	private String TAG = "FLAG";
	static Properties p = new Properties();
	static {
		try {
			// load accoutn properties configuration from classpath
			p.load(TerminateMatchingAMITag.class.getResourceAsStream("/config.properties"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		new TerminateMatchingAMITag().terminateTagInstances();
	}

	public void terminateTagInstances() throws Exception {
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

					if (image == null) {
						// cant decide ami
						continue;
					}
					Tag tag = findAndReturnTag(image, TAG);

					if (tag == null) {
						// not contains tag, cant take action
						continue;
					}
					if (hasTagValue(tag)) {
						// Tag value is correct, add to terminate
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