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
  IonList,
  IonListHeader,
  IonModal,
  IonPage,
  IonProgressBar,
  IonText,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import {
  carSportOutline,
  clipboardOutline,
  receiptOutline,
  sparklesOutline,
  warningOutline,
} from "ionicons/icons";
import { TEXT } from "../../../constants/texts";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import {
  inspectionAddValidationSchema,
  initialInspectionValues,
  inspectionFormtoInspection,
  InspectionForm,
} from "./InspectionValidationSchema";
import FormDate from "../../../components/Form/FormDate";
import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import {
  CarBodyDamageModel,
  InspectionExpenseModel,
  InspectionModel,
} from "../../../constants/CarModels";
import FormInput from "../../../components/Form/FormInput";
import FormSelect from "../../../components/Form/FormSelect";
import { CLEANING, INTEGRITY, TIRE_BRANDS } from "../../../constants/selectOptions";
import FormSelectFilterAdd from "../../../components/Form/FormSelectFilterAdd";
import { TIRE_BRANDS_KEY } from "../../../services/localStorage/localstorage";
import ExpenseAddModal from "../ExpenseAddModal/ExpenseAddModal";
import { ExpenseModelActive } from "../ExpenseAddModal/expenseAddValidationSchema";
import ItemNotFound from "../../../components/List/ItemNotFound";
import BodyDamageAdd from "../../BodyDamage/BodyDamageAddModal/BodyDamageAdd";
import BodyDamage from "../../BodyDamage/BodyDamage";
import { useLocation, useHistory } from "react-router";
import { currencyFormat } from "../../../services/currencyFormat";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import { getApiErrorMessage } from "../../../services/apiErrorMessage";
import { buildInvalidFieldsMessage } from "../../../services/formErrors";

interface InspectionAddModalProps {
  closeModal: (response?: InspectionModel) => void;
  initialValues?: InspectionModel;
  carId: string;
}

const INSPECTION_FORM_FIELDS: Array<keyof InspectionForm> = [
  "id",
  "date",
  "driverName",
  "odometer",
  "internalCleaning",
  "externalCleaning",
  "leftFrontId",
  "rightFrontId",
  "leftBackId",
  "rightBackId",
  "spareId",
  "leftFrontModel",
  "rightFrontModel",
  "leftBackModel",
  "rightBackModel",
  "spareModel",
  "leftFrontIntegrity",
  "rightFrontIntegrity",
  "leftBackIntegrity",
  "rightBackIntegrity",
  "spareIntegrity",
  "expenses",
  "carBodyDamages",
];

const INSPECTION_FIELD_LABELS: Record<string, string> = {
  date: TEXT.date,
  driverName: TEXT.driver,
  odometer: TEXT.odometer,
  internalCleaning: `${TEXT.cleaning} ${TEXT.intern}`,
  externalCleaning: `${TEXT.cleaning} ${TEXT.extern}`,
  leftFrontModel: `${TEXT.leftFront} - ${TEXT.model}`,
  rightFrontModel: `${TEXT.rightFront} - ${TEXT.model}`,
  leftBackModel: `${TEXT.leftFBack} - ${TEXT.model}`,
  rightBackModel: `${TEXT.rightFBack} - ${TEXT.model}`,
  spareModel: `${TEXT.spare} - ${TEXT.model}`,
  leftFrontIntegrity: `${TEXT.leftFront} - ${TEXT.integrity}`,
  rightFrontIntegrity: `${TEXT.rightFront} - ${TEXT.integrity}`,
  leftBackIntegrity: `${TEXT.leftFBack} - ${TEXT.integrity}`,
  rightBackIntegrity: `${TEXT.rightFBack} - ${TEXT.integrity}`,
  spareIntegrity: `${TEXT.spare} - ${TEXT.integrity}`,
};

const InspectionAdd: React.FC<InspectionAddModalProps> = ({ closeModal, initialValues, carId }) => {
  const location = useLocation();
  const nav = useHistory();
  const { showErrorAlert } = useAlert();

  const [isLoading, setIsLoading] = useState(false);
  const [isExpenseModalOpen, setIsExpenseModalOpen] = useState(false);
  const [isBodyDamageModalOpen, setIsBodyDamageModalOpen] = useState(false);
  const [activeExpense, setActiveExpense] = useState<ExpenseModelActive>({});
  const [activeCarBodyDamage, setActiveCarBodyDamage] = useState<CarBodyDamageModel>({});

  const initialValuesKey = JSON.stringify(initialValues || {});
  const formInitial = useMemo(
    () => initialInspectionValues(initialValues || {}),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initialValuesKey]
  );

  useEffect(() => {
    if (!location.search.includes("modalExpenseOpened=true")) {
      setIsExpenseModalOpen(false);
      setIsBodyDamageModalOpen(false);
    }
  }, [location]);

  const {
    handleSubmit,
    setValue,
    getValues,
    watch,
    reset,
    register,
    formState: { errors },
  } = useForm<InspectionForm>({
    reValidateMode: "onBlur",
    resolver: yupResolver(inspectionAddValidationSchema),
    defaultValues: formInitial,
  });

  const updateField = useCallback(
    (field: keyof InspectionForm, value: any) => {
      setValue(field as any, value, {
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true,
      });
    },
    [setValue]
  );

  useEffect(() => {
    INSPECTION_FORM_FIELDS.forEach((field) => register(field as any));
  }, [register]);

  const loadActiveBodyDamages = useCallback(async () => {
    setIsLoading(true);
    try {
      const { data } = await api.get(
        endpoints.BODY_DAMAGE_ACTIVE({ pathVariables: { id: carId } })
      );
      if (data) setValue("carBodyDamages", data);
    } catch (error) {
      showErrorAlert(getApiErrorMessage(error, TEXT.loadCarDamagesFailed));
    } finally {
      setIsLoading(false);
    }
  }, [carId, setValue, showErrorAlert]);

  useEffect(() => {
    reset(formInitial);
  }, [formInitial, reset]);

  useEffect(() => {
    if (formInitial.id === undefined) {
      void loadActiveBodyDamages();
    }
  }, [formInitial.id, loadActiveBodyDamages]);

  const expensesList = watch("expenses") || [];
  const carDamagesList = watch("carBodyDamages") || [];

  const closeExpenseModal = useCallback(
    (response?: ExpenseModelActive) => {
      setIsExpenseModalOpen(false);
      nav.goBack();

      if (!response) return;

      const finalExpenseArray = [...(getValues().expenses || [])];

      if (response.delete) {
        if (typeof response.activeIndex === "number") {
          delete finalExpenseArray[response.activeIndex];
          updateField("expenses", finalExpenseArray.filter((n) => n));
        }
        return;
      }

      if (typeof response.activeIndex === "number") {
        finalExpenseArray[response.activeIndex] = response;
        updateField("expenses", finalExpenseArray);
        return;
      }

      finalExpenseArray.push(response);
      updateField("expenses", finalExpenseArray);
    },
    [getValues, updateField, nav]
  );

  const closeBodyDamageModal = useCallback(
    (response?: CarBodyDamageModel) => {
      setIsBodyDamageModalOpen(false);
      nav.goBack();

      if (!response) return;

      const finalcarBodyDamageArray = [...(getValues().carBodyDamages || [])];
      const indexFound = finalcarBodyDamageArray.findIndex(
        (bodyDamage) => bodyDamage.id === response.id
      );
      if (indexFound === -1) finalcarBodyDamageArray.push(response);
      else finalcarBodyDamageArray[indexFound] = response;

      updateField("carBodyDamages", finalcarBodyDamageArray);
    },
    [getValues, updateField, nav]
  );

  const onSubmit = async (newInspectionForm: InspectionForm) => {
    const newInspection = inspectionFormtoInspection(newInspectionForm);
    setIsLoading(true);

    try {
      let responseInspection: InspectionModel;

      if (newInspection.id) {
        const urlPatch = endpoints.INSPECTIONS_EDIT({ pathVariables: { id: newInspection.id } });
        const response = await api.put(urlPatch, newInspection);
        responseInspection = response.data;
      } else {
        const urlPost = endpoints.INSPECTIONS({ pathVariables: { id: carId } });
        const response = await api.post(urlPost, newInspection);
        responseInspection = response.data;
      }

      closeModal(responseInspection);
    } catch (e) {
      showErrorAlert(
        getApiErrorMessage(e, TEXT.saveFailed, {
          idexists: "Esta inspeção já possui identificador e não pode ser criada novamente.",
          idinvalid: "A inspeção aberta não corresponde ao registro enviado.",
          idnull: "A inspeção não possui identificador para edição.",
          notcurrentuser: "Este carro ou inspeção não foi encontrado para o usuário atual.",
        })
      );
    } finally {
      setIsLoading(false);
    }
  };

  const onInvalid = (invalidErrors: unknown) => {
    showErrorAlert(buildInvalidFieldsMessage(invalidErrors, INSPECTION_FIELD_LABELS));
  };

  const onDelete = async () => {
    if (!formInitial.id) return;
    setIsLoading(true);

    try {
      await api.delete(endpoints.INSPECTIONS_EDIT({ pathVariables: { id: formInitial.id } }));
      closeModal({ id: formInitial.id, delete: true });
    } catch (e) {
      showErrorAlert(getApiErrorMessage(e, TEXT.deleteFailed));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <IonPage id="car-inspection-add-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonButton
              fill="clear"
              className="app-outline-btn"
              onClick={() => closeModal()}
            >
              {TEXT.cancel}
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.addCarInspection}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              className="app-primary-btn"
              disabled={isLoading}
              onClick={handleSubmit(onSubmit, onInvalid)}
            >
              {TEXT.save}
            </IonButton>
          </IonButtons>
          {isLoading && <IonProgressBar type="indeterminate" />}
        </IonToolbar>
      </IonHeader>

      <IonContent>
        <div className="app-shell app-shell--compact">
          <section className="app-section">
            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={clipboardOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.addCarInspection}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Informe data, motorista e odometro da inspecao.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <form
                  className="app-form-grid"
                  onSubmit={(e) => e.preventDefault()}
                >
                  <FormDate
                    id="date-inspection-add"
                    initialValue={String(watch("date") || "")}
                    label={TEXT.date}
                    presentation="date"
                    errorsObj={errors}
                    errorName="date"
                    formCallBack={(value: string) => updateField("date", value)}
                  />

                  <FormInput
                    label={TEXT.driver}
                    errorsObj={errors}
                    errorName="driverName"
                    initialValue={watch("driverName") ?? ""}
                    maxlength={50}
                    changeCallback={(value: string) =>
                      updateField("driverName", value)
                    }
                  />

                  <FormInput
                    label={TEXT.odometer}
                    errorsObj={errors}
                    errorName="odometer"
                    initialValue={watch("odometer") ?? 0}
                    maxlength={15}
                    type="number"
                    changeCallback={(value: number) =>
                      updateField("odometer", value)
                    }
                  />
                </form>
              </IonCardContent>
            </IonCard>

            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={sparklesOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.cleaning}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Avalie a limpeza interna e externa do veiculo.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <form
                  className="app-form-grid"
                  onSubmit={(e) => e.preventDefault()}
                >
                  <FormSelect
                    header={TEXT.cleaning}
                    label={TEXT.intern}
                    options={CLEANING}
                    errorsObj={errors}
                    errorName="internalCleaning"
                    initialValue={watch("internalCleaning") ?? ""}
                    changeCallback={(value: string) =>
                      updateField("internalCleaning", value)
                    }
                  />

                  <FormSelect
                    label={TEXT.extern}
                    options={CLEANING}
                    errorsObj={errors}
                    errorName="externalCleaning"
                    initialValue={watch("externalCleaning") ?? ""}
                    changeCallback={(value: string) =>
                      updateField("externalCleaning", value)
                    }
                  />
                </form>
              </IonCardContent>
            </IonCard>

            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={carSportOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.tire}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Confira modelo e integridade de cada pneu.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                {/* pneus... (mantive como estava) */}
                <form
                  className="app-form-grid"
                  onSubmit={(e) => e.preventDefault()}
                >
                  <FormSelectFilterAdd
                    header={`${TEXT.tire} ${TEXT.leftFront}`}
                    label={TEXT.model}
                    errorsObj={errors}
                    errorName="leftFrontModel"
                    formCallBack={(value: string) =>
                      updateField("leftFrontModel", value)
                    }
                    initialValue={watch("leftFrontModel") ?? ""}
                    options={TIRE_BRANDS}
                    storageToken={TIRE_BRANDS_KEY}
                  />
                  <FormSelect
                    label={TEXT.integrity}
                    options={INTEGRITY}
                    errorsObj={errors}
                    errorName="leftFrontIntegrity"
                    initialValue={watch("leftFrontIntegrity") ?? ""}
                    changeCallback={(value: string) =>
                      updateField("leftFrontIntegrity", value)
                    }
                  />

                  <FormSelectFilterAdd
                    header={`${TEXT.tire} ${TEXT.rightFront}`}
                    label={TEXT.model}
                    errorsObj={errors}
                    errorName="rightFrontModel"
                    formCallBack={(value: string) =>
                      updateField("rightFrontModel", value)
                    }
                    initialValue={watch("rightFrontModel") ?? ""}
                    options={TIRE_BRANDS}
                    storageToken={TIRE_BRANDS_KEY}
                  />
                  <FormSelect
                    label={TEXT.integrity}
                    options={INTEGRITY}
                    errorsObj={errors}
                    errorName="rightFrontIntegrity"
                    initialValue={watch("rightFrontIntegrity") ?? ""}
                    changeCallback={(value: string) =>
                      updateField("rightFrontIntegrity", value)
                    }
                  />

                  <FormSelectFilterAdd
                    header={`${TEXT.tire} ${TEXT.leftFBack}`}
                    label={TEXT.model}
                    errorsObj={errors}
                    errorName="leftBackModel"
                    formCallBack={(value: string) =>
                      updateField("leftBackModel", value)
                    }
                    initialValue={watch("leftBackModel") ?? ""}
                    options={TIRE_BRANDS}
                    storageToken={TIRE_BRANDS_KEY}
                  />
                  <FormSelect
                    label={TEXT.integrity}
                    options={INTEGRITY}
                    errorsObj={errors}
                    errorName="leftBackIntegrity"
                    initialValue={watch("leftBackIntegrity") ?? ""}
                    changeCallback={(value: string) =>
                      updateField("leftBackIntegrity", value)
                    }
                  />

                  <FormSelectFilterAdd
                    header={`${TEXT.tire} ${TEXT.rightFBack}`}
                    label={TEXT.model}
                    errorsObj={errors}
                    errorName="rightBackModel"
                    formCallBack={(value: string) =>
                      updateField("rightBackModel", value)
                    }
                    initialValue={watch("rightBackModel") ?? ""}
                    options={TIRE_BRANDS}
                    storageToken={TIRE_BRANDS_KEY}
                  />
                  <FormSelect
                    label={TEXT.integrity}
                    options={INTEGRITY}
                    errorsObj={errors}
                    errorName="rightBackIntegrity"
                    initialValue={watch("rightBackIntegrity") ?? ""}
                    changeCallback={(value: string) =>
                      updateField("rightBackIntegrity", value)
                    }
                  />

                  <FormSelectFilterAdd
                    header={`${TEXT.tire} ${TEXT.spare}`}
                    label={TEXT.model}
                    errorsObj={errors}
                    errorName="spareModel"
                    formCallBack={(value: string) =>
                      updateField("spareModel", value)
                    }
                    initialValue={watch("spareModel") ?? ""}
                    options={TIRE_BRANDS}
                    storageToken={TIRE_BRANDS_KEY}
                  />
                  <FormSelect
                    label={TEXT.integrity}
                    options={INTEGRITY}
                    errorsObj={errors}
                    errorName="spareIntegrity"
                    initialValue={watch("spareIntegrity") ?? ""}
                    changeCallback={(value: string) =>
                      updateField("spareIntegrity", value)
                    }
                  />
                </form>
              </IonCardContent>
            </IonCard>

            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={receiptOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.inspectionsExpenses}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Despesas relacionadas a esta inspecao.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <IonList>
                  <IonListHeader>
                    <IonLabel>
                      <h1>{TEXT.inspectionsExpenses}</h1>
                    </IonLabel>
                    <IonButton
                      fill="clear"
                      className="app-outline-btn"
                      onClick={(e) => {
                        e.preventDefault();
                        setActiveExpense({});
                        setIsExpenseModalOpen(true);
                        nav.push(
                          nav.location.pathname +
                            "?modalOpened=true&modalExpenseOpened=true"
                        );
                      }}
                    >
                      {TEXT.add}
                    </IonButton>
                  </IonListHeader>

                  {expensesList.map(
                    (expense: InspectionExpenseModel, index) =>
                      expense ? (
                        <IonItem
                          button
                          key={index}
                          onClick={(e) => {
                            e.preventDefault();
                            setActiveExpense({ ...expense, activeIndex: index });
                            setIsExpenseModalOpen(true);
                            nav.push(
                              nav.location.pathname +
                                "?modalOpened=true&modalExpenseOpened=true"
                            );
                          }}
                        >
                          <IonLabel slot="end">
                            <IonText>{currencyFormat(expense?.cost)}</IonText>
                          </IonLabel>
                          <IonLabel class="ion-text-wrap">
                            <h2>
                              <IonText color="medium">{`${expense?.ammount} ${expense?.name}`}</IonText>
                            </h2>
                          </IonLabel>
                        </IonItem>
                      ) : null
                  )}

                  {expensesList.length === 0 && (
                    <ItemNotFound />
                  )}
                </IonList>
              </IonCardContent>
            </IonCard>

            <IonCard className="app-panel-card">
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon app-soft-icon--warning">
                  <IonIcon icon={warningOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.carDamages}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Avarias identificadas nesta inspecao.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <IonList>
                  <IonListHeader>
                    <IonLabel>
                      <h1>{TEXT.carDamages}</h1>
                    </IonLabel>
                    <IonButton
                      fill="clear"
                      className="app-outline-btn"
                      onClick={(e) => {
                        e.preventDefault();
                        setActiveCarBodyDamage({
                          date: watch("date"),
                          responsible: watch("driverName"),
                        });
                        setIsBodyDamageModalOpen(true);
                        nav.push(
                          nav.location.pathname +
                            "?modalOpened=true&modalExpenseOpened=true"
                        );
                      }}
                    >
                      {TEXT.add}
                    </IonButton>
                  </IonListHeader>

                  {carDamagesList.map(
                    (carBodyDamage: CarBodyDamageModel, index) =>
                      carBodyDamage ? (
                        <IonItem
                          button
                          key={index}
                          onClick={(e) => {
                            e.preventDefault();
                            setActiveCarBodyDamage({ ...carBodyDamage });
                            setIsBodyDamageModalOpen(true);
                            nav.push(
                              nav.location.pathname +
                                "?modalOpened=true&modalExpenseOpened=true"
                            );
                          }}
                        >
                          <BodyDamage carDamage={carBodyDamage} />
                        </IonItem>
                      ) : null
                  )}

                  {carDamagesList.length === 0 && <ItemNotFound />}
                </IonList>
              </IonCardContent>
            </IonCard>

            {formInitial.id && (
              <div>
                <FormDeleteButton
                  label={`${TEXT.delete} ${TEXT.inspection}`}
                  message={TEXT.inspection}
                  callBackFunc={onDelete}
                />
              </div>
            )}
          </section>
        </div>
      </IonContent>

      <IonModal isOpen={isExpenseModalOpen} backdropDismiss={false}>
        <ExpenseAddModal closeModal={closeExpenseModal} initialValues={activeExpense} />
      </IonModal>

      <IonModal isOpen={isBodyDamageModalOpen} backdropDismiss={false}>
        <BodyDamageAdd closeModal={closeBodyDamageModal} carId={carId} initialValues={activeCarBodyDamage} />
      </IonModal>
    </IonPage>
  );
};

export default InspectionAdd;
