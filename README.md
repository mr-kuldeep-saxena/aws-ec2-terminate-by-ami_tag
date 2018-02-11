TerminateNotMatchingTag.java -
	A simple service to terminate instances according to given tag value. An instance will be terminated if - 
		1. it does not belong to owner (self) created AMI
		2. it does not contain mentioned TAG in AMI
		3. The tag does not contain the correct value as mentioned. 

TerminateMatchingTag.java - It does oppposite of above service. terminates instances which match given tag.


NOTE - Take caution to run this code, as this will terminate all the instances which are not meeting criteria.

Same can be easily implemented to use instance's tags. Just remove AMI description code and use instance tag in place of AMI tags


Refer to Setup.doc - Which provides steps to configure this as lambda function and uses cloud watch to schedule it to run
