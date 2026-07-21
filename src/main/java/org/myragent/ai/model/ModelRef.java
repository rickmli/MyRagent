package org.myragent.ai.model;

import org.myragent.ai.config.AIModelProperties;

public record ModelRef(
        String id,
        AIModelProperties.ModelCandidate candidate,
        AIModelProperties.ProviderConfig provider
) {
}