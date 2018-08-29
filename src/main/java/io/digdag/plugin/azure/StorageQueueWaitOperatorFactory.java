package io.digdag.plugin.azure;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueClient;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
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

public class StorageQueueWaitOperatorFactory implements OperatorFactory {

    private static Logger logger = LoggerFactory.getLogger(StorageQueueWaitOperatorFactory.class);

    private static final DurationInterval POLL_INTERVAL = DurationInterval.of(Duration.ofSeconds(5), Duration.ofMinutes(5));

    public String getType() {
        return "storage_queue_wait";
    }

    @Override
    public Operator newOperator(OperatorContext context) {
        return new StorageQueueWaitOperator(context);
    }

    private class StorageQueueWaitOperator implements Operator {

        private final TaskRequest request;
        private final TaskState state;
        private final SecretProvider secrets;

        public StorageQueueWaitOperator(OperatorContext context) {

            this.request = context.getTaskRequest();
            this.state = TaskState.of(request);
            this.secrets = context.getSecrets();
        }

        @Override
        public TaskResult run() {
            Config params = request.getConfig().mergeDefault(
                request.getConfig().getNestedOrGetEmpty("azure"));

            String queueName = params.get("_command", String.class);

            SecretProvider azureSecrets = secrets.getSecrets("azure").getSecrets("queue");
            String connectionString = azureSecrets.getSecret("connectionString");

            // Create Client
            CloudQueueClient client = buildClient(connectionString);

            CloudQueueMessage message = pollingWaiter(state, "EXISTS")
                    .withPollInterval(POLL_INTERVAL)
                    .withWaitMessage("Message in '%s/' does not peek", queueName)
                    .await(pollState -> pollingRetryExecutor(pollState, "POLL")
                    .retryUnless(StorageException.class, e -> true)
                    .run(s -> {
                            CloudQueue q = client.getQueueReference(queueName);
                            CloudQueueMessage peekedMessage = q.peekMessage();
                            if (peekedMessage != null) {
                                return Optional.of(peekedMessage);
                            } else {
                                return Optional.absent();
                            }
                    }));

            return TaskResult.defaultBuilder(request)
                    .resetStoreParams(ImmutableList.of(ConfigKey.of("queue", "last_object")))
                    .storeParams(storeParams(message))
                    .build();

        }
        private Config storeParams(CloudQueueMessage message)
        {
            Config params = request.getConfig().getFactory().create();
            Config object = params.getNestedOrSetEmpty("queue").getNestedOrSetEmpty("last_object");
            object.set("messageId", message.getId());
            object.set("insertionTime", message.getInsertionTime());
            object.set("expirationTime", message.getExpirationTime());

            return params;
        }

        private CloudQueueClient buildClient(String connectionString) {

            CloudStorageAccount storageAccount = null;
            try {
                storageAccount = CloudStorageAccount.parse(connectionString);
                return storageAccount.createCloudQueueClient();
            } catch (URISyntaxException | InvalidKeyException e) {
                throw new ConfigException("Invalid Storage Account ConnectionString", e);
            }
        }
    }


}
