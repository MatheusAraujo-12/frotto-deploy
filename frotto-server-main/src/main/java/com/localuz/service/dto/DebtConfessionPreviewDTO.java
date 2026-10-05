package com.localuz.service.dto;

import com.localuz.domain.enumeration.PendencyStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only data of a Confissão de Dívida built from pendencies owed by one driver. Contract and car fields at the top
 * are filled only when all items share them; each item always carries its own origin. Values come from the database,
 * never from the client: each item is the outstanding balance of one pendency.
 */
public class DebtConfessionPreviewDTO {

    private Long driverCarId;
    private String contractNumber;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private Boolean contractConcluded;
    private Long driverId;
    private String driverName;
    private String driverCpf;
    private Long carId;
    private String carPlate;
    private String carModel;
    private String origemDaDivida;
    private List<Item> items = new ArrayList<>();
    private BigDecimal valorTotal;

    public Long getDriverCarId() {
        return driverCarId;
    }

    public void setDriverCarId(Long driverCarId) {
        this.driverCarId = driverCarId;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public LocalDate getContractStartDate() {
        return contractStartDate;
    }

    public void setContractStartDate(LocalDate contractStartDate) {
        this.contractStartDate = contractStartDate;
    }

    public LocalDate getContractEndDate() {
        return contractEndDate;
    }

    public void setContractEndDate(LocalDate contractEndDate) {
        this.contractEndDate = contractEndDate;
    }

    public Boolean getContractConcluded() {
        return contractConcluded;
    }

    public void setContractConcluded(Boolean contractConcluded) {
        this.contractConcluded = contractConcluded;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverCpf() {
        return driverCpf;
    }

    public void setDriverCpf(String driverCpf) {
        this.driverCpf = driverCpf;
    }

    public Long getCarId() {
        return carId;
    }

    public void setCarId(Long carId) {
        this.carId = carId;
    }

    public String getCarPlate() {
        return carPlate;
    }

    public void setCarPlate(String carPlate) {
        this.carPlate = carPlate;
    }

    public String getCarModel() {
        return carModel;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public String getOrigemDaDivida() {
        return origemDaDivida;
    }

    public void setOrigemDaDivida(String origemDaDivida) {
        this.origemDaDivida = origemDaDivida;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    /** One pendency of the confession: its snapshot plus the item fields of the document payload. */
    public static class Item {

        private Long pendencyId;
        private String name;
        private LocalDate date;
        private String note;
        private PendencyStatus status;
        private BigDecimal cost;
        private BigDecimal paidAmount;
        private BigDecimal remainingAmount;
        private Long typeId;
        private String typeNameSnapshot;
        private String descricaoItem;
        private BigDecimal valorItem;
        private Long originDriverCarId;
        private String originContractNumber;
        private Long originCarId;
        private String originCarPlate;
        private String originCarModel;

        public Long getOriginDriverCarId() {
            return originDriverCarId;
        }

        public void setOriginDriverCarId(Long originDriverCarId) {
            this.originDriverCarId = originDriverCarId;
        }

        public String getOriginContractNumber() {
            return originContractNumber;
        }

        public void setOriginContractNumber(String originContractNumber) {
            this.originContractNumber = originContractNumber;
        }

        public Long getOriginCarId() {
            return originCarId;
        }

        public void setOriginCarId(Long originCarId) {
            this.originCarId = originCarId;
        }

        public String getOriginCarPlate() {
            return originCarPlate;
        }

        public void setOriginCarPlate(String originCarPlate) {
            this.originCarPlate = originCarPlate;
        }

        public String getOriginCarModel() {
            return originCarModel;
        }

        public void setOriginCarModel(String originCarModel) {
            this.originCarModel = originCarModel;
        }

        public Long getPendencyId() {
            return pendencyId;
        }

        public void setPendencyId(Long pendencyId) {
            this.pendencyId = pendencyId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public PendencyStatus getStatus() {
            return status;
        }

        public void setStatus(PendencyStatus status) {
            this.status = status;
        }

        public BigDecimal getCost() {
            return cost;
        }

        public void setCost(BigDecimal cost) {
            this.cost = cost;
        }

        public BigDecimal getPaidAmount() {
            return paidAmount;
        }

        public void setPaidAmount(BigDecimal paidAmount) {
            this.paidAmount = paidAmount;
        }

        public BigDecimal getRemainingAmount() {
            return remainingAmount;
        }

        public void setRemainingAmount(BigDecimal remainingAmount) {
            this.remainingAmount = remainingAmount;
        }

        public Long getTypeId() {
            return typeId;
        }

        public void setTypeId(Long typeId) {
            this.typeId = typeId;
        }

        public String getTypeNameSnapshot() {
            return typeNameSnapshot;
        }

        public void setTypeNameSnapshot(String typeNameSnapshot) {
            this.typeNameSnapshot = typeNameSnapshot;
        }

        public String getDescricaoItem() {
            return descricaoItem;
        }

        public void setDescricaoItem(String descricaoItem) {
            this.descricaoItem = descricaoItem;
        }

        public BigDecimal getValorItem() {
            return valorItem;
        }

        public void setValorItem(BigDecimal valorItem) {
            this.valorItem = valorItem;
        }
    }
}
