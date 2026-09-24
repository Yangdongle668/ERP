package com.erp.module.system.service.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/** 当前实例标识（主机名:端口），用于定时任务锁与后台任务归属；重启后不变。 */
@Component
public class NodeId {

    private final String value;

    public NodeId(@Value("${server.port:8080}") String port) {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "localhost";
        }
        this.value = host + ":" + port;
    }

    public String value() {
        return value;
    }
}
