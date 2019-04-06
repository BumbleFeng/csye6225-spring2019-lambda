import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

public class LogEvent implements RequestHandler<SNSEvent, Object> {
    public Object handleRequest(SNSEvent request, Context context) {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation started: " + timeStamp);
        String message = request.getRecords().get(0).getSNS().getMessage();
        context.getLogger().log(message);
        String[] json = message.split("\"");
        String email = json[3];
        String token = json[7];

        DynamoDbClient dynamoDbClient = DynamoDbClient.create();
        HashMap<String, AttributeValue> item = new HashMap<>();
        item.put("email", AttributeValue.builder().s(email).build());
        Map<String, AttributeValue> returned = dynamoDbClient.getItem(GetItemRequest.builder().tableName("csye6225").key(item).build()).item();
        long time = System.currentTimeMillis() / 1000L;
        if (returned.get("ttl") != null && Long.parseLong(returned.get("ttl").n()) > time) {
            context.getLogger().log("Already send email to: " + email);
            timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
            context.getLogger().log("Invocation completed: " + timeStamp);
            return null;
        }

        item.put("token", AttributeValue.builder().s(token).build());
        time += 1200L;
        item.put("ttl", AttributeValue.builder().n(Long.toString(time)).build());
        dynamoDbClient.putItem(PutItemRequest.builder().tableName("csye6225").item(item).build());

        String domain = System.getenv("domain");
        String from = "no-reply@" + domain;
        String subject = "Reset Password";
        String text = "https://" + domain + "/reset?email=" + email + "&token=" + token;
        SesClient sesClient = SesClient.create();
        sesClient.sendEmail(SendEmailRequest.builder()
                .destination(Destination.builder().toAddresses(email).build())
                .message(Message.builder()
                        .subject(Content.builder().charset("UTF-8").data(subject).build())
                        .body(Body.builder().text(Content.builder().charset("UTF-8").data(text).build()).build())
                        .build())
                .source(from)
                .build());

        context.getLogger().log("Send email to: " + email);
        timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation completed: " + timeStamp);
        return null;
    }

} 