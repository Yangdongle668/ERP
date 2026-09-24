package com.erp.module.system.service.file;

import com.erp.module.system.dal.dataobject.FileDO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 本地磁盘存储（默认）。根目录 erp.file.local-path，默认 ./data/files。 */
@Component
@ConditionalOnProperty(name = "erp.file.storage", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${erp.file.local-path:./data/files}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String type() {
        return FileDO.STORAGE_LOCAL;
    }

    @Override
    public void put(String path, InputStream content) throws IOException {
        Path target = resolve(path);
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public InputStream open(String path) throws IOException {
        return Files.newInputStream(resolve(path));
    }

    @Override
    public void delete(String path) throws IOException {
        Files.deleteIfExists(resolve(path));
    }

    /** 防止路径穿越：结果必须位于根目录下 */
    private Path resolve(String path) {
        Path p = root.resolve(path).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("非法的附件路径: " + path);
        return p;
    }
}
