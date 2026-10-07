package com.sap.service.judger;
import com.fasterxml.jackson.databind.*;
import com.sap.entity.judger.OjNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service @RequiredArgsConstructor
public class JudgerNodeTransport {
    private final ObjectMapper json;
    private final JudgerNodeSecrets secrets;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    public JsonNode request(OjNode node,String method,String path,Object body,int timeout,String lease) {
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(node.getEndpoint()+path)).timeout(Duration.ofSeconds(timeout))
                .header("Authorization","Bearer "+secrets.token(node));
            if(lease!=null) request.header("X-Judge-Lease",lease);
            if(body==null) request.method(method,HttpRequest.BodyPublishers.noBody());
            else request.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            // One run returns at most 320 KiB of streams. Allow JSON escaping
            // and metadata, but keep status/control replies much smaller.
            int limit="/engine/run".equals(path)?4*1024*1024:64*1024;
            pending=http.sendAsync(request.build(),info -> new BoundedResponseSubscriber(limit));
            // Covers the complete body, including a peer trickling chunks after headers.
            HttpResponse<byte[]> response=pending.get(timeout,TimeUnit.SECONDS);
            if(response.statusCode()==409) throw new NodeCapacityException();
            if(response.statusCode()/100!=2) throw new NodeUnavailableException();
            String content=new String(response.body(),StandardCharsets.UTF_8);
            return content.isBlank()?json.createObjectNode():json.readTree(content);
        } catch(NodeUnavailableException|NodeCapacityException e) { throw e; }
        catch(InterruptedException e) {Thread.currentThread().interrupt();throw new NodeUnavailableException();}
        catch(Exception e) {throw new NodeUnavailableException();}
        finally {if(pending!=null&&!pending.isDone())pending.cancel(true);}
    }
    public static class NodeCapacityException extends RuntimeException {}
}
