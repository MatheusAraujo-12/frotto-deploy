import {
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonMenuButton,
  IonPage,
  IonProgressBar,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import api from "../../services/axios/axios";
import endpoints from "../../constants/endpoints";
import { TEXT } from "../../constants/texts";
import { useEffect, useState } from "react";
import { useAlert } from "../../services/hooks/useAlert";
import { RouteComponentProps } from "react-router";
import { stringDateToDB } from "../../services/dateFormat";
import { yupResolver } from "@hookform/resolvers/yup";
import { useForm } from "react-hook-form";
import {
  initialReportsValues,
  ReportModel,
  reportsValidationSchema,
} from "./reportsValidationSchema";
import FormSelect from "../../components/Form/FormSelect";
import { REPORTS, REPORT_PERIODS } from "../../constants/selectOptions";
import FormDate from "../../components/Form/FormDate";
import {
  createHistoryReport,
  createMaintenanceReport,
  createMonthlyReport,
} from "./pdfMaker";
import { readerOutline } from "ionicons/icons";
import { resolvePeriodRange } from "./reportPeriod";
import CarSelector, { normalizeCars } from "../../components/Car/CarSelector";
import { CarModel } from "../../constants/CarModels";
import "./Reports.css";

interface IncomeDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Reports: React.FC<IncomeDetail> = () => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [reportValue, setReportValue] = useState(REPORTS.month);

  const [groupList, setGroupList] = useState<string[]>([]);
  const [groupCars, setGroupCars] = useState<CarModel[]>([]);
  const [loadingGroupCars, setLoadingGroupCars] = useState(false);
  const [selectedCar, setSelectedCar] = useState<CarModel | null>(null);
  const [showCarSelector, setShowCarSelector] = useState(false);

  const {
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(reportsValidationSchema),
    defaultValues: initialReportsValues(),
  });

  const group = watch("group");
  const period = watch("period");

  useEffect(() => {
    setSelectedCar(null);
    setShowCarSelector(false);

    if (reportValue !== REPORTS.history || !group) {
      setGroupCars([]);
      return;
    }

    let mounted = true;
    const loadGroupCars = async () => {
      setLoadingGroupCars(true);
      try {
        const { data } = await api.get(endpoints.CARS_ACTIVE());
        if (!mounted) return;
        const normalized = Array.isArray(data) ? normalizeCars(data) : [];
        setGroupCars(normalized.filter((car) => car.group === group));
      } catch (error) {
        if (mounted) setGroupCars([]);
      } finally {
        if (mounted) setLoadingGroupCars(false);
      }
    };
    loadGroupCars();

    return () => {
      mounted = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [group, reportValue]);

  const loadGroups = async () => {
    setisLoading(true);
    try {
      const { data } = await api.get(endpoints.CARS_ACTIVE_GROUPS());
      setisLoading(false);
      if (data) {
        setGroupList(data);
      }
    } catch (error) {
      setisLoading(false);
      showErrorAlert(TEXT.loadCarGroupsFailed);
    }
  };

  useEffect(() => {
    loadGroups();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onSubmit = async (reportForm: ReportModel) => {
    setisLoading(true);
    try {
      if (reportForm.report === REPORTS.month) {
        const { data } = await api.get(
          endpoints.REPORTS({
            query: {
              group: reportForm.group,
              date: stringDateToDB(reportForm.date),
            },
          })
        );
        createMonthlyReport(data);
      }

      if (reportForm.report === REPORTS.history) {
        const periodRange = resolvePeriodRange(
          reportForm.period,
          reportForm.customStartDate,
          reportForm.customEndDate
        );
        const { data } = await api.get(
          endpoints.REPORTS_HISTORY({
            query: {
              group: reportForm.group,
              startDate: periodRange.startDate,
              endDate: periodRange.endDate,
              carId: selectedCar?.id,
            },
          })
        );
        createHistoryReport(data, reportForm.group, periodRange, selectedCar?.name);
      }

      if (reportForm.report === REPORTS.maintenance) {
        const { data } = await api.get(
          endpoints.REPORTS_MAINTENANCE({
            query: {
              group: reportForm.group,
              year: reportForm.year,
            },
          })
        );
        createMaintenanceReport(
          data,
          reportForm.group,
          reportForm.year.toString()
        );
      }

      setisLoading(false);
    } catch (e: any) {
      setisLoading(false);
      showErrorAlert(TEXT.saveFailed);
    }
  };

  return (
    <IonPage id="reports-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonMenuButton menu="main-menu" autoHide={false}></IonMenuButton>
          </IonButtons>
          <IonTitle>{TEXT.reports}</IonTitle>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="app-shell app-shell--compact">
          <section className="app-section">
            <div className="reports-section-head">
              <h2 className="app-section-title">{TEXT.reports}</h2>
              <p className="app-section-subtitle">
                Gere relatórios financeiros e de manutenção por grupo.
              </p>
            </div>

            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={readerOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.generateReport}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Selecione o tipo de relatório, o grupo e o período desejado.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>

              <IonCardContent>
                <form
                  className="app-form-grid reports-form"
                  onSubmit={(event) => event.preventDefault()}
                >
                  <FormSelect
                    label={TEXT.report}
                    options={Object.values(REPORTS)}
                    errorsObj={errors}
                    errorName="report"
                    initialValue={watch("report")}
                    changeCallback={(value: REPORTS) => {
                      setValue("report", value);
                      setReportValue(value);
                    }}
                    required
                  />
                  <FormSelect
                    label={TEXT.group}
                    options={groupList}
                    errorsObj={errors}
                    errorName="group"
                    initialValue={watch("group")}
                    changeCallback={(value: string) => {
                      setValue("group", value);
                    }}
                    required
                  />
                  {reportValue === REPORTS.month && (
                    <FormDate
                      id="date-reports"
                      initialValue={watch("date").toString()}
                      label={TEXT.date}
                      presentation="month-year"
                      formCallBack={(value: string) => {
                        setValue("date", value);
                      }}
                    />
                  )}
                  {reportValue === REPORTS.history && (
                    <>
                      <FormSelect
                        label={TEXT.period}
                        options={Object.values(REPORT_PERIODS)}
                        errorsObj={errors}
                        errorName="period"
                        initialValue={watch("period")}
                        changeCallback={(value: REPORT_PERIODS) => {
                          setValue("period", value);
                        }}
                        required
                      />
                      {period === REPORT_PERIODS.custom && (
                        <>
                          <FormDate
                            id="history-start-date"
                            initialValue={watch("customStartDate")}
                            label={TEXT.dateStart}
                            presentation="date"
                            error={errors.customStartDate?.message}
                            formCallBack={(value: string) => {
                              setValue("customStartDate", value);
                            }}
                          />
                          <FormDate
                            id="history-end-date"
                            initialValue={watch("customEndDate")}
                            label={TEXT.dateEnd}
                            presentation="date"
                            error={errors.customEndDate?.message}
                            formCallBack={(value: string) => {
                              setValue("customEndDate", value);
                            }}
                          />
                        </>
                      )}

                      {!selectedCar && !showCarSelector && (
                        <IonItem className="app-form-item" lines="none">
                          <IonLabel>{TEXT.allCarsInGroup}</IonLabel>
                          <IonButton
                            slot="end"
                            fill="outline"
                            size="small"
                            disabled={!group || loadingGroupCars || groupCars.length === 0}
                            onClick={() => setShowCarSelector(true)}
                          >
                            {TEXT.selectSpecificCar}
                          </IonButton>
                        </IonItem>
                      )}

                      {!selectedCar && showCarSelector && groupCars.length > 0 && (
                        <div className="reports-car-selector">
                          <CarSelector
                            cars={groupCars}
                            onSelect={(car) => {
                              setSelectedCar(car);
                              setShowCarSelector(false);
                            }}
                          />
                          <IonButton
                            fill="clear"
                            size="small"
                            onClick={() => setShowCarSelector(false)}
                          >
                            {TEXT.cancel}
                          </IonButton>
                        </div>
                      )}

                      {selectedCar && (
                        <IonItem className="app-form-item" lines="none">
                          <IonLabel>
                            <div>{selectedCar.name}</div>
                            <div>{selectedCar.plate || ""}</div>
                          </IonLabel>
                          <IonButton
                            slot="end"
                            fill="outline"
                            size="small"
                            onClick={() => setSelectedCar(null)}
                          >
                            {TEXT.removeCarFilter}
                          </IonButton>
                        </IonItem>
                      )}
                    </>
                  )}
                  {reportValue === REPORTS.maintenance && (
                    <FormDate
                      id="year-reports"
                      initialValue={watch("year").toString()}
                      label={TEXT.year}
                      presentation="year"
                      min="2021"
                      formCallBack={(value: string) => {
                        setValue("year", Number(value));
                      }}
                    />
                  )}

                  <IonButton
                    expand="block"
                    className="app-primary-btn reports-submit-btn"
                    onClick={handleSubmit(onSubmit)}
                    disabled={isLoading}
                  >
                    {TEXT.generateReport}
                  </IonButton>
                </form>
              </IonCardContent>
            </IonCard>
          </section>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default Reports;
