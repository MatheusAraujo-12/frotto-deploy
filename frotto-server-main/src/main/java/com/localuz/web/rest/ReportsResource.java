package com.localuz.web.rest;

import com.localuz.domain.Car;
import com.localuz.domain.CarExpense;
import com.localuz.domain.CarHistory;
import com.localuz.domain.Expense;
import com.localuz.domain.Income;
import com.localuz.domain.Inspection;
import com.localuz.domain.Maintenance;
import com.localuz.domain.Service;
import com.localuz.repository.CarExpenseRepository;
import com.localuz.repository.CarHistoryRepository;
import com.localuz.repository.CarRepository;
import com.localuz.repository.IncomeRepository;
import com.localuz.repository.InspectionRepository;
import com.localuz.repository.MaintenanceRepository;
import com.localuz.service.dto.Reports.GroupReportDTO;
import com.localuz.service.dto.Reports.ReportHistoryDTO;
import com.localuz.service.dto.Reports.ReportItemDTO;
import com.localuz.service.dto.Reports.ReportsDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.HeaderUtil;

/** REST controller for managing Reports. */
@RestController
@RequestMapping("/api")
@Transactional
public class ReportsResource {

    private final Logger log = LoggerFactory.getLogger(ReportsResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "reports";
    public static final BigDecimal ONE_HUNDRED = new BigDecimal(100);
    public static final BigDecimal ZERO = new BigDecimal(0);

    private final CarRepository carRepository;
    private final IncomeRepository incomeRepository;
    private final CarExpenseRepository carExpenseRepository;
    private final InspectionRepository inspectionRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final CarHistoryRepository carHistoryRepository;

    public ReportsResource(
        CarRepository carRepository,
        IncomeRepository incomeRepository,
        CarExpenseRepository carExpenseRepository,
        InspectionRepository inspectionRepository,
        MaintenanceRepository maintenanceRepository,
        CarHistoryRepository carHistoryRepository
    ) {
        this.carRepository = carRepository;
        this.incomeRepository = incomeRepository;
        this.carExpenseRepository = carExpenseRepository;
        this.inspectionRepository = inspectionRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.carHistoryRepository = carHistoryRepository;
    }

    @GetMapping("/reports")
    public GroupReportDTO getReports(@RequestParam String group, @RequestParam LocalDate date) {
        if (!group.isEmpty() && date != null) {
            List<Car> carsInGroup = carRepository.findActiveByCurrentUserAndGroup(group);
            int month = date.getMonthValue();
            int year = date.getYear();
            LocalDate yearMonthLastDay = date.withDayOfMonth(date.lengthOfMonth());

            GroupReportDTO groupReportDTO = new GroupReportDTO(group, yearMonthLastDay);
            BigDecimal groupEarnings = new BigDecimal(0);
            BigDecimal groupExpenses = new BigDecimal(0);
            BigDecimal groupAdminFee = new BigDecimal(0);
            BigDecimal groupNetEarnings = new BigDecimal(0);
            BigDecimal groupInvested = new BigDecimal(0);

            List<ReportsDTO> reportsDTOList = new ArrayList<>();
            List<CarHistory> carHistoryList = new ArrayList<>();
            for (Car car : carsInGroup) {
                List<Income> incomes = incomeRepository.findByCarIdAndMonthYear(car.getId(), month, year);
                List<CarExpense> carExpenses = carExpenseRepository.findByCarIdAndMonthYear(car.getId(), month, year);
                List<Inspection> inspections = inspectionRepository.findByCarIdAndMonthYear(car.getId(), month, year);
                List<Maintenance> maintenances = maintenanceRepository.findByCarIdAndMonthYear(car.getId(), month, year);

                Float reportOdometer = car.getOdometer();
                if (maintenances != null && !maintenances.isEmpty() && maintenances.size() > 0) {
                    reportOdometer = maintenances.get(maintenances.size() - 1).getOdometer();
                }

                String carName = car.getName() + " " + car.getPlate();
                ReportsDTO reportsDTO = new ReportsDTO(
                    car.getId(),
                    carName,
                    yearMonthLastDay,
                    reportOdometer,
                    car.getColor(),
                    car.getInitialValue(),
                    car.getYear()
                );
                List<ReportItemDTO> incomesDTO = new ArrayList<>();
                List<ReportItemDTO> carExpensesDTO = new ArrayList<>();
                List<ReportItemDTO> inspectionsDTO = new ArrayList<>();
                List<ReportItemDTO> maintenancesDTO = new ArrayList<>();

                BigDecimal carEarningsDTO = new BigDecimal(0);
                BigDecimal expensesValueDTO = new BigDecimal(0);
                BigDecimal adminFeeDTO = new BigDecimal(0);
                BigDecimal netEarningsDTO = new BigDecimal(0);

                for (Income item : incomes) {
                    incomesDTO.add(new ReportItemDTO(item.getName(), item.getDate(), item.getCost()));
                    carEarningsDTO = carEarningsDTO.add(item.getCost());
                }
                reportsDTO.setIncomes(incomesDTO);

                for (CarExpense item : carExpenses) {
                    carExpensesDTO.add(new ReportItemDTO(item.getName(), item.getDate(), item.getCost()));
                    expensesValueDTO = expensesValueDTO.add(item.getCost());
                }
                reportsDTO.setCarExpenses(carExpensesDTO);

                for (Inspection item : inspections) {
                    for (Expense subItem : item.getExpenses()) {
                        inspectionsDTO.add(new ReportItemDTO(subItem.getName(), item.getDate(), subItem.getCost()));
                        expensesValueDTO = expensesValueDTO.add(subItem.getCost());
                    }
                }
                reportsDTO.setInspections(inspectionsDTO);

                for (Maintenance item : maintenances) {
                    for (Service subItem : item.getServices()) {
                        maintenancesDTO.add(new ReportItemDTO(subItem.getName(), item.getDate(), subItem.getCost()));
                        expensesValueDTO = expensesValueDTO.add(subItem.getCost());
                    }
                }
                reportsDTO.setMaintenances(maintenancesDTO);

                BigDecimal netEarningsWithoutFee = carEarningsDTO.subtract(expensesValueDTO);
                if (
                    netEarningsWithoutFee.compareTo(BigDecimal.valueOf(0)) == 1 &&
                    car.getAdministrationFee() != null &&
                    car.getAdministrationFee() > 0
                ) {
                    adminFeeDTO = netEarningsWithoutFee.multiply(BigDecimal.valueOf(car.getAdministrationFee())).divide(ONE_HUNDRED);
                }
                netEarningsDTO = netEarningsWithoutFee.subtract(adminFeeDTO);

                reportsDTO.setEarnings(carEarningsDTO);
                reportsDTO.setExpenses(expensesValueDTO);
                reportsDTO.setAdminFee(adminFeeDTO);
                reportsDTO.setNetEarnings(netEarningsDTO);
                reportsDTO.setPercentageProfit(calculatePercentage(car.getInitialValue(), netEarningsDTO));

                groupEarnings = groupEarnings.add(carEarningsDTO);
                groupExpenses = groupExpenses.add(expensesValueDTO);
                groupAdminFee = groupAdminFee.add(adminFeeDTO);
                groupNetEarnings = groupNetEarnings.add(netEarningsDTO);
                groupInvested = groupInvested.add(car.getInitialValue());

                reportsDTOList.add(reportsDTO);
                carHistoryList.add(
                    new CarHistory(
                        reportsDTO.getCarId(),
                        reportsDTO.getDate(),
                        reportsDTO.getEarnings(),
                        reportsDTO.getExpenses(),
                        reportsDTO.getAdminFee(),
                        reportsDTO.getNetEarnings(),
                        reportsDTO.getOdometer()
                    )
                );
            }
            carHistoryRepository.saveAll(carHistoryList);
            groupReportDTO.setReportsDTO(reportsDTOList);
            groupReportDTO.setGroupEarnings(groupEarnings);
            groupReportDTO.setGroupExpenses(groupExpenses);
            groupReportDTO.setGroupAdminFee(groupAdminFee);
            groupReportDTO.setGroupNetEarnings(groupNetEarnings);
            groupReportDTO.setGroupInvested(groupInvested);
            groupReportDTO.setGroupPercentageProfit(calculatePercentage(groupInvested, groupNetEarnings));
            return groupReportDTO;
        }

        return null;
    }

    @PostMapping("/reports")
    public ResponseEntity<Void> createReports(@RequestBody List<ReportsDTO> reportsDTO) {
        List<CarHistory> carHistoryList = new ArrayList<>();
        for (ReportsDTO reportDto : reportsDTO) {
            carHistoryList.add(
                new CarHistory(
                    reportDto.getCarId(),
                    reportDto.getDate(),
                    reportDto.getEarnings(),
                    reportDto.getExpenses(),
                    reportDto.getAdminFee(),
                    reportDto.getNetEarnings(),
                    reportDto.getOdometer()
                )
            );
        }
        carHistoryRepository.saveAll(carHistoryList);

        return ResponseEntity.noContent().headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, "")).build();
    }

    @GetMapping("/reports-history")
    public List<ReportHistoryDTO> getReportsHistory(@RequestParam String group) {
        List<ReportHistoryDTO> responseList = new ArrayList<>();
        if (!group.isEmpty()) {
            List<Car> carsInGroup = carRepository.findActiveByCurrentUserAndGroup(group);
            for (Car car : carsInGroup) {
                String carName = car.getName() + " " + car.getPlate();
                ReportHistoryDTO responseItem = new ReportHistoryDTO();
                responseItem.setCarId(car.getId());
                responseItem.setCarName(carName);
                responseItem.setColor(car.getColor());
                responseItem.setYear(car.getYear());
                responseItem.setInitialValue(car.getInitialValue());
                responseItem.setCarHistory(new ArrayList<>());

                BigDecimal totalEarnings = new BigDecimal(0);
                BigDecimal totalExpenses = new BigDecimal(0);
                BigDecimal totalAdminFee = new BigDecimal(0);
                BigDecimal totalNetEarnings = new BigDecimal(0);
                BigDecimal totalPercentageProfit = new BigDecimal(0);
                BigDecimal averagePercentageProfit = new BigDecimal(0);

                List<CarHistory> carHistory = carHistoryRepository.findAllByCarId(car.getId());

                if (carHistory != null && !carHistory.isEmpty()) {
                    responseItem.setCarHistory(carHistory);
                    for (CarHistory carHis : carHistory) {
                        totalEarnings = totalEarnings.add(carHis.getEarnings());
                        totalExpenses = totalExpenses.add(carHis.getExpenses());
                        totalAdminFee = totalAdminFee.add(carHis.getAdminFee());
                        totalNetEarnings = totalNetEarnings.add(carHis.getNetEarnings());
                    }
                    totalPercentageProfit = calculatePercentage(car.getInitialValue(), totalNetEarnings);
                    averagePercentageProfit = totalPercentageProfit.divide(BigDecimal.valueOf(carHistory.size()), 2, RoundingMode.HALF_UP);
                }

                responseItem.setTotalEarnings(totalEarnings);
                responseItem.setTotalExpenses(totalExpenses);
                responseItem.setTotalAdminFee(totalAdminFee);
                responseItem.setTotalNetEarnings(totalNetEarnings);
                responseItem.setTotalPercentageProfit(totalPercentageProfit);
                responseItem.setAveragePercentageProfit(averagePercentageProfit);

                responseList.add(responseItem);
            }
        }
        return responseList;
    }

    @GetMapping("/reports/maintenance")
    public List<ReportsDTO> getReportMaintenance(@RequestParam String group, @RequestParam Integer year) {
        List<ReportsDTO> reportsDTOList = new ArrayList<>();

        if (!group.isEmpty() && year != null) {
            List<Car> carsInGroup = carRepository.findActiveByCurrentUserAndGroup(group);

            for (Car car : carsInGroup) {
                List<Maintenance> maintenances = maintenanceRepository.findByCarIdAndYear(car.getId(), year);

                String carName = car.getName() + " " + car.getPlate();
                ReportsDTO reportsDTO = new ReportsDTO(
                    car.getId(),
                    carName,
                    LocalDate.now(),
                    car.getOdometer(),
                    car.getColor(),
                    car.getInitialValue(),
                    car.getYear()
                );

                List<ReportItemDTO> maintenancesDTO = new ArrayList<>();

                for (Maintenance item : maintenances) {
                    for (Service subItem : item.getServices()) {
                        maintenancesDTO.add(new ReportItemDTO(subItem.getName(), item.getDate(), subItem.getCost()));
                    }
                }

                reportsDTO.setMaintenances(maintenancesDTO);

                reportsDTOList.add(reportsDTO);
            }
        }

        return reportsDTOList;
    }

    private BigDecimal calculatePercentage(BigDecimal total, BigDecimal profit) {
        if (total != null && total.compareTo(ZERO) != 0) {
            return profit.multiply(ONE_HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
        }
        return ZERO;
    }
}
