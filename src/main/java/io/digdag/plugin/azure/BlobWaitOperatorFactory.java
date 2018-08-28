package io.digdag.plugin.azure;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.ListBlobItem;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigException;
import io.digdag.client.config.ConfigKey;
import io.digdag.spi.*;
import io.digdag.standards.operator.DurationInterval;
import io.digdag.standards.operator.state.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.Duration;

import static io.digdag.standards.operator.state.PollingRetryExecutor.pollingRetryExecutor;
import static io.digdag.standards.operator.state.PollingWaiter.pollingWaiter;

public class BlobWaitOperatorFactory implements OperatorFactory {

    private static Logger logger = LoggerFactory.getLogger(BlobWaitOperatorFactory.class);

    private static final DurationInterval POLL_INTERVAL = DurationInterval.of(Duration.ofSeconds(5), Duration.ofMinutes(5));

    public String getType() {
        return "blob_wait";
    }

    @Override
    public Operator newOperator(OperatorContext context) {
        return new BlobWaitOperator(context);
    }

    private class BlobWaitOperator implements Operator {

        private final TaskRequest request;
        private final TaskState state;
        private final SecretProvider secrets;

        public BlobWaitOperator(OperatorContext context) {

            this.request = context.getTaskRequest();
            this.state = TaskState.of(request);
            this.secrets = context.getSecrets();
        }

        @Override
        public TaskResult run() {
            Config params = request.getConfig().mergeDefault(
                request.getConfig().getNestedOrGetEmpty("azure"));

            String path = params.get("_command", String.class);
            String container = params.get("container", String.class);

            SecretProvider azureSecrets = secrets.getSecrets("azure").getSecrets("blob");
            String connectionString = azureSecrets.getSecret("connectionString");

            // Create Client
            CloudBlobClient client = buildClient(connectionString);

            CloudBlob firstBlob = pollingWaiter(state, "EXISTS")
                    .withPollInterval(POLL_INTERVAL)
                    .withWaitMessage("Object '%s/%s' does not yet exist", container, path)
                    .await(pollState -> pollingRetryExecutor(pollState, "POLL")
                    .retryUnless(StorageException.class, e -> true)
                    .run(s -> {
                            CloudBlobContainer cr = client.getContainerReference(container);
                            for(ListBlobItem item : cr.listBlobs(path, true)) {
                                if (item instanceof  CloudBlob) {
                                    return Optional.of((CloudBlob)item);
                                }
                            }
                            return Optional.absent();
                    }));

            return TaskResult.defaultBuilder(request)
                    .resetStoreParams(ImmutableList.of(ConfigKey.of("blob", "last_object")))
                    .storeParams(storeParams(firstBlob))
                    .build();

        }
        private Config storeParams(CloudBlob item)
        {
            Config params = request.getConfig().getFactory().create();
            Config object = params.getNestedOrSetEmpty("blob").getNestedOrSetEmpty("last_object");
            object.set("name", item.getName());
            object.set("metadata", item.getMetadata());
            object.set("properties", item.getProperties());

            return params;
        }

        private CloudBlobClient buildClient(String connectionString) {

            CloudStorageAccount storageAccount = null;
            try {
                storageAccount = CloudStorageAccount.parse(connectionString);
                return storageAccount.createCloudBlobClient();
            } catch (URISyntaxException | InvalidKeyException e) {
                throw new ConfigException("Invalid Storage Account ConnectionString", e);
            }
        }
    }


}
