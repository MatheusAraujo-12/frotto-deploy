package com.localuz.service.dto;

/** Result of GrandfatheringService#apply. subscriptionId is null when nothing was created. */
public class GrandfatherResultDTO {

    private final GrandfatherPreviewDTO preview;
    private final boolean subscriptionCreated;
    private final Long subscriptionId;

    public GrandfatherResultDTO(GrandfatherPreviewDTO preview, boolean subscriptionCreated, Long subscriptionId) {
        this.preview = preview;
        this.subscriptionCreated = subscriptionCreated;
        this.subscriptionId = subscriptionId;
    }

    public GrandfatherPreviewDTO getPreview() {
        return preview;
    }

    public boolean isSubscriptionCreated() {
        return subscriptionCreated;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }
}
