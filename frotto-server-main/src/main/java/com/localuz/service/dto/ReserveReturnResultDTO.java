package com.localuz.service.dto;

/** Outcome of returning a reserve car: the reserve is concluded; the primary contract is restored only when safe. */
public class ReserveReturnResultDTO {

    /** RESTORED, or why the primary stayed as it was. */
    public enum Outcome {
        RESTORED,
        /** Another driver is operating the primary car: the primary stays SUSPENDED (nobody is removed). */
        PRIMARY_CAR_OCCUPIED,
        /** The driver is already operating another car: the primary stays SUSPENDED. */
        DRIVER_ACTIVE_ELSEWHERE,
        /** The primary was concluded meanwhile: nothing to restore. */
        PRIMARY_CONCLUDED,
    }

    private Long returnedDriverCarId;
    private Long primaryDriverCarId;
    private String primaryCarPlate;
    private Outcome outcome;
    private boolean primaryRestored;
    private Long occupyingDriverCarId;
    private String occupyingDriverName;

    public Long getReturnedDriverCarId() {
        return returnedDriverCarId;
    }

    public void setReturnedDriverCarId(Long returnedDriverCarId) {
        this.returnedDriverCarId = returnedDriverCarId;
    }

    public Long getPrimaryDriverCarId() {
        return primaryDriverCarId;
    }

    public void setPrimaryDriverCarId(Long primaryDriverCarId) {
        this.primaryDriverCarId = primaryDriverCarId;
    }

    public String getPrimaryCarPlate() {
        return primaryCarPlate;
    }

    public void setPrimaryCarPlate(String primaryCarPlate) {
        this.primaryCarPlate = primaryCarPlate;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    public boolean isPrimaryRestored() {
        return primaryRestored;
    }

    public void setPrimaryRestored(boolean primaryRestored) {
        this.primaryRestored = primaryRestored;
    }

    public Long getOccupyingDriverCarId() {
        return occupyingDriverCarId;
    }

    public void setOccupyingDriverCarId(Long occupyingDriverCarId) {
        this.occupyingDriverCarId = occupyingDriverCarId;
    }

    public String getOccupyingDriverName() {
        return occupyingDriverName;
    }

    public void setOccupyingDriverName(String occupyingDriverName) {
        this.occupyingDriverName = occupyingDriverName;
    }
}
