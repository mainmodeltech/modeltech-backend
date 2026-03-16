package com.modeltech.datamasteryhub.modules.networking.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    UploadResult upload(MultipartFile file, String folder);

    void delete(String objectKey);

    public record UploadResult(String objectKey, String url) {}
}
