import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
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
import { REPORTS } from "../../constants/selectOptions";
import FormDate from "../../components/Form/FormDate";
import {
  createHistoryReport,
  createMaintenanceReport,
  createMonthlyReport,
} from "./pdfMaker";

interface IncomeDetail
  extends RouteComponentProps<{
    id: string;
  }> {}

const Reports: React.FC<IncomeDetail> = () => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [reportValue, setReportValue] = useState(REPORTS.month);

  const [groupList, setGroupList] = useState<string[]>([]);

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
        const { data } = await api.get(
          endpoints.REPORTS_HISTORY({
            query: {
              group: reportForm.group,
            },
          })
        );
        createHistoryReport(data, reportForm.group);
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
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="secondary">
            <IonButton>
              <IonMenuButton menu="main-menu"></IonMenuButton>
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.reports}</IonTitle>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <div className="section-shell">
          <form>
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
              onClick={handleSubmit(onSubmit)}
              disabled={isLoading}
            >
              {TEXT.generateReport}
            </IonButton>
          </form>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default Reports;
