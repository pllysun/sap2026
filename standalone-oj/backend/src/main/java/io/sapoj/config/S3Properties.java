package io.sapoj.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Data @Component @ConfigurationProperties(prefix="storage.s3")
public class S3Properties {
    private boolean enabled=false;
    private String endpoint="http://rustfs:9000",region="us-east-1",bucket="sap-oj-private",accessKey="",secretKey="";
    private boolean pathStyle=true,createBucket=false;
}
