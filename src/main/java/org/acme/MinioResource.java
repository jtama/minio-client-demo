package org.acme;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;

@Produces(MediaType.TEXT_PLAIN)
@Path("/minio")
public class MinioResource {

    // Over sizing chunks
    private static final long PART_SIZE = 50 * 1024 * 1024;
    public static final String BUCKET_NAME = "test";
    public static final String NO_SUCH_KEY = "NoSuchKey";

    @Inject
    public Logger logger;

    @Inject
    MinioClient minioClient;

    @POST
    public String addObject(@RestForm("file") FileUpload file) throws IOException, MinioException, GeneralSecurityException {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
        }
        byte[] content = Files.readAllBytes(file.uploadedFile());
        try (InputStream is = new ByteArrayInputStream(content)) {
            ObjectWriteResponse response = minioClient
                    .putObject(
                            PutObjectArgs.builder()
                                    .bucket(BUCKET_NAME)
                                    .object(file.fileName())
                                    .contentType(file.contentType())
                                    .stream(is, -1, PART_SIZE)
                                    .build());
            return response.bucket() + "/" + response.object();
        } catch (MinioException | GeneralSecurityException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @GET
    public String listObject()
            throws IOException, MinioException, GeneralSecurityException {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build())) {
            logger.warn("Nope, doesn't exists");
            throw new NotFoundException("Bucket %s doesn't exist".formatted(BUCKET_NAME));
        }
        var results = StreamSupport.stream(minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(BUCKET_NAME)
                        .build()).spliterator(), false);
        return results.map(item -> silentGet(item).objectName())
            .collect(Collectors.joining(","));
    }

    @Path("{fileName}")
    @GET
    public String getObjectContent(String fileName)
            throws IOException, MinioException, GeneralSecurityException {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build())) {
            throw new NotFoundException("Bucket %s doesn't exist".formatted(BUCKET_NAME));
        }
        try (InputStream is = minioClient
                .getObject(
                        GetObjectArgs.builder()
                                .bucket(BUCKET_NAME)
                                .object(fileName)
                                .build())) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (ErrorResponseException e){
            if (e.errorResponse().code().equals(NO_SUCH_KEY)){
                throw new NotFoundException(String.format("Object %s doesn't exist", fileName));
            }
            throw e;
        }
    }

    private Item silentGet(Result<Item> item) {
        try {
            return item.get();
        } catch (IOException|MinioException|GeneralSecurityException e) {
            throw new InternalServerErrorException(e);
        }
    }
}
