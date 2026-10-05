package com.localuz.service.dto;

import java.util.List;

/** Pendencies selected by the user to be formalized in a Confissão de Dívida. */
public class DebtConfessionPreviewRequest {

    private List<Long> pendencyIds;

    public List<Long> getPendencyIds() {
        return pendencyIds;
    }

    public void setPendencyIds(List<Long> pendencyIds) {
        this.pendencyIds = pendencyIds;
    }
}
