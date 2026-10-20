package io.sapoj.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapoj.common.BusinessException;
import io.sapoj.config.S3Properties;
import io.sapoj.service.judger.OjService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Private administrator archives only. Never issues public URLs for hidden data. */
@Service @RequiredArgsConstructor
public class ProblemArchives {
    private final S3Properties config;
    private final OjService oj;
    private final ObjectMapper json;
    private S3Client client;
    private synchronized S3Client client(){
        if(!config.isEnabled())throw new BusinessException(503,"对象存储未启用，请在部署配置中设置 S3");
        if(client==null){
            var uri=URI.create(config.getEndpoint());
            if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null
                ||config.getAccessKey().isBlank()||config.getSecretKey().isBlank()||!config.getBucket().matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
                throw new BusinessException(503,"对象存储部署配置不完整");
            client=S3Client.builder().endpointOverride(uri).region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.getAccessKey(),config.getSecretKey())))
                .forcePathStyle(config.isPathStyle()).httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(20)))
                .overrideConfiguration(b->b.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(25))).build();
        }
        return client;
    }
    private void bucket(){
        var c=client();
        try{c.headBucket(b->b.bucket(config.getBucket()));}
        catch(S3Exception e){
            if(e.statusCode()!=404||!config.isCreateBucket())throw new BusinessException(503,"对象存储桶不可用，请检查网络、桶和权限");
            try{c.createBucket(b->b.bucket(config.getBucket()));}
            catch(BucketAlreadyOwnedByYouException ignored){}
        }
    }
    public Map<String,Object> status(){
        return Map.of("enabled",config.isEnabled(),"bucket",config.getBucket(),"region",config.getRegion(),"pathStyle",config.isPathStyle());
    }
    public Map<String,Object> check(){
        try{bucket();return Map.of("available",true,"bucket",config.getBucket());}
        catch(BusinessException e){throw e;}catch(Exception e){throw unavailable();}
    }
    public Map<String,Object> archive(Long id){
        var problem=oj.detail(id,true);
        try{
            byte[] bytes=json.writeValueAsBytes(problem.get("pack"));
            if(bytes.length>4_000_000)throw new BusinessException(400,"归档题包不能超过 4 MB");
            bucket();String key="problem-archives/"+UUID.randomUUID()+".json";
            client().putObject(b->b.bucket(config.getBucket()).key(key).contentType("application/json"),RequestBody.fromBytes(bytes));
            return Map.of("key",key,"problemId",id,"revision",problem.get("revision"),"bytes",bytes.length);
        }catch(BusinessException e){throw e;}catch(Exception e){throw unavailable();}
    }
    public List<Map<String,Object>> list(){
        try{bucket();return client().listObjectsV2(b->b.bucket(config.getBucket()).prefix("problem-archives/").maxKeys(100))
            .contents().stream().map(o->Map.<String,Object>of("key",o.key(),"bytes",o.size(),"updatedAt",o.lastModified().toString())).toList();}
        catch(BusinessException e){throw e;}catch(Exception e){throw unavailable();}
    }
    public byte[] read(String id){
        if(!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw new BusinessException(400,"归档 ID 无效");
        try{
            var request=GetObjectRequest.builder().bucket(config.getBucket()).key("problem-archives/"+id+".json").build();
            try(var stream=client().getObject(request)){
                byte[] bytes=stream.readNBytes(4_000_001);
                if(bytes.length>4_000_000)throw new BusinessException(400,"归档文件过大");
                return bytes;
            }
        }catch(NoSuchKeyException e){throw new BusinessException(404,"归档不存在");}
        catch(BusinessException e){throw e;}catch(Exception e){throw unavailable();}
    }
    private BusinessException unavailable(){return new BusinessException(503,"对象存储请求失败，请检查部署配置与服务状态");}
    @PreDestroy public synchronized void close(){if(client!=null)client.close();}
}
