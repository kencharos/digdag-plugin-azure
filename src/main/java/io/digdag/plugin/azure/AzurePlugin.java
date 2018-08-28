package io.digdag.plugin.azure;

import io.digdag.spi.OperatorFactory;
import io.digdag.spi.OperatorProvider;
import io.digdag.spi.Plugin;

import java.util.Arrays;
import java.util.List;

public class AzurePlugin implements Plugin {
    @Override
    public <T> Class<? extends T> getServiceProvider(Class<T> type) {
        if (type == OperatorProvider.class) {
            return AzureOperatorProvider.class.asSubclass(type);
        } else {
            return null;
        }
    }

    public static class AzureOperatorProvider implements OperatorProvider {

        @Override
        public List<OperatorFactory> get() {
            return Arrays.asList(new BlobWaitOperatorFactory());
        }
    }
}
