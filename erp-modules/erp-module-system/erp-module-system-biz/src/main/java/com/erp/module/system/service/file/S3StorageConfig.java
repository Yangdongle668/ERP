package com.erp.module.system.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** erp.file.storage=s3 时启用 S3 兼容对象存储（新附件写入对象存储，历史本地附件仍按记录的存储类型读取）。 */
@Configuration
@ConditionalOnProperty(name = "erp.file.storage", havingValue = "s3")
public class S3StorageConfig {

    @Bean
    public S3FileStorage s3FileStorage(@Value("${erp.file.s3.endpoint:}") String endpoint, @Value("${erp.file.s3.region:}") String region,
                                       @Value("${erp.file.s3.bucket:}") String bucket, @Value("${erp.file.s3.access-key:}") String accessKey,
                                       @Value("${erp.file.s3.secret-key:}") String secretKey, @Value("${erp.file.s3.path-style:true}") boolean pathStyle,
                                       @Value("${erp.file.s3.prefix:}") String prefix) {
        return new S3FileStorage(new S3FileStorage.Config(endpoint, region, bucket, accessKey, secretKey, pathStyle, prefix));
    }
}
