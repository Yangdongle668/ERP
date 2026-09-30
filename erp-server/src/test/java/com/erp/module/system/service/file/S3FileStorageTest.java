package com.erp.module.system.service.file;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** S3 签名（AWS Signature V4）与地址拼接 */
class S3FileStorageTest {

    /** AWS 文档示例“GET Object”（examplebucket / test.txt / Range bytes=0-9）的已知签名 */
    @Test
    void signatureMatchesAwsExample() {
        S3FileStorage s = new S3FileStorage(new S3FileStorage.Config("https://s3.amazonaws.com", "us-east-1", "examplebucket",
                "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", false, ""));
        assertThat(s.host()).isEqualTo("examplebucket.s3.amazonaws.com");
        assertThat(s.canonicalUri("test.txt")).isEqualTo("/test.txt");
        String auth = s.authorization("GET", "/test.txt", "", Map.of("host", "examplebucket.s3.amazonaws.com", "range", "bytes=0-9",
                        "x-amz-content-sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "x-amz-date", "20130524T000000Z"),
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ZonedDateTime.of(2013, 5, 24, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(auth).isEqualTo("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,"
                + "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date,Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41");
    }

    @Test
    void pathStyleUriWithPrefixAndEncoding() {
        S3FileStorage s = new S3FileStorage(new S3FileStorage.Config("http://minio:9000/", "", "erp", "ak", "sk", true, "erp"));
        assertThat(s.host()).isEqualTo("minio:9000");
        assertThat(s.key("2026/09/a.pdf")).isEqualTo("erp/2026/09/a.pdf");
        assertThat(s.canonicalUri("2026/09/报 价.pdf")).isEqualTo("/erp/erp/2026/09/%E6%8A%A5%20%E4%BB%B7.pdf");
        assertThat(s.uri("2026/09/a.pdf").toString()).isEqualTo("http://minio:9000/erp/erp/2026/09/a.pdf");
    }
}
