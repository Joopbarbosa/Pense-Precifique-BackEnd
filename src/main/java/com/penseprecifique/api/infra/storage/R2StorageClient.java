package com.penseprecifique.api.infra.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * Cliente pro bucket Cloudflare R2 (compatível S3) — DT-NOVA-4. Só fala com o bucket (upload/
 * remoção de objeto); validação de formato/tamanho (RN-NOVA-6) é responsabilidade do Service que
 * chama este cliente, nunca daqui — mesma separação já usada em {@code PdfMicroservicoClient}
 * (cliente HTTP burro, regra de negócio fica no módulo de domínio).
 *
 * <p>{@code forcePathStyle(true)} é obrigatório para R2 — sem isso o SDK monta a URL no formato
 * virtual-hosted-style ({@code bucket.endpoint}), que o R2 não resolve.
 */
@Component
@Slf4j
public class R2StorageClient {

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicUrlBase;

    public R2StorageClient(
            @Value("${r2.access-key-id}") String accessKeyId,
            @Value("${r2.secret-access-key}") String secretAccessKey,
            @Value("${r2.endpoint}") String endpoint,
            @Value("${r2.bucket-name}") String bucketName,
            @Value("${r2.public-url}") String publicUrl) {
        this.bucketName = bucketName;
        this.publicUrlBase = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .forcePathStyle(true)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    /** Sobe o objeto e devolve a URL pública ({@code r2.public-url + "/" + key}). */
    public String upload(String key, byte[] conteudo, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(conteudo));
        return publicUrlBase + "/" + key;
    }

    /** Melhor esforço — remoção de objeto antigo nunca deve derrubar a requisição de troca/edição. */
    public void deletarPorUrl(String url) {
        if (url == null || !url.startsWith(publicUrlBase)) return;
        String key = url.substring(publicUrlBase.length() + 1);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        } catch (Exception e) {
            log.warn("Falha ao remover objeto antigo do R2 (key={}): {}", key, e.getMessage());
        }
    }
}
