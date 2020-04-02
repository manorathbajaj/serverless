import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.time.Instant;

public class SendEmail implements RequestHandler<SNSEvent,Object> {

    static com.amazonaws.services.dynamodbv2.document.DynamoDB dynamoDb;
    private String tableName = "csye6225";
    private String domain = System.getenv("DOMAIN_NAME");

    @Override
    public Object handleRequest(SNSEvent input, Context context) {
        LambdaLogger logger = context.getLogger();

        logger.log("in lambda");

        String message = input.getRecords().get(0).getSNS().getMessage();
        logger.log("message: " + message);

        String parts[] = message.split(",");
        AmazonDynamoDBClient dynamoDBClient = new AmazonDynamoDBClient();
        dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1));
        this.dynamoDb = new com.amazonaws.services.dynamodbv2.document.DynamoDB(dynamoDBClient);
        Item item = this.dynamoDb.getTable("csye6225").getItem("id",parts[0]);
        if(item == null || (item != null && Long.parseLong(item.get("TTL").toString()) < Instant.now().getEpochSecond())) {
            this.dynamoDb.getTable("csye6225").putItem(new PutItemSpec()
                    .withItem(new Item().withString("id", parts[0]).withLong("TTL", (60*60) + Instant.now().getEpochSecond())));

            String emailBody = "Bills due \n";
            for(int i = 1; i<parts.length;i++) {
                emailBody = emailBody + parts[i] + "\n";
            }
            Content subject = new Content().withData("Due Bills");
            Content bodys = new Content().withData(emailBody);
            Body body = new Body().withText(bodys);
            Message messages = new Message().withSubject(subject).withBody(body);

            SendEmailRequest emailRequest = new SendEmailRequest()
                    .withDestination(new Destination().withToAddresses(parts[0])).withMessage(messages).withSource("email-service@"+domain);

            AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1).build();

            client.sendEmail(emailRequest);
        }
        return null;
    }

}
