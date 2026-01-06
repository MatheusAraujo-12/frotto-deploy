import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
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

interface InspectionAddModalProps {
  closeModal: (response?: InspectionModel) => void;
  initialValues?: InspectionModel;
  carId: string;
}

const InspectionAdd: React.FC<InspectionAddModalProps> = ({ closeModal, initialValues, carId }) => {
  const location = useLocation();
  const nav = useHistory();
  const { showErrorAlert } = useAlert();

  const [isLoading, setIsLoading] = useState(false);
  const [isExpenseModalOpen, setIsExpenseModalOpen] = useState(false);
  const [isBodyDamageModalOpen, setIsBodyDamageModalOpen] = useState(false);
  const [activeExpense, setActiveExpense] = useState<ExpenseModelActive>({});
  const [activeCarBodyDamage, setActiveCarBodyDamage] = useState<CarBodyDamageModel>({});

  const formInitial = initialInspectionValues(initialValues || {});

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
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(inspectionAddValidationSchema),
    defaultValues: formInitial,
  });

  const loadActiveBodyDamages = async () => {
    setIsLoading(true);
    try {
      const { data } = await api.get(
        endpoints.BODY_DAMAGE_ACTIVE({ pathVariables: { id: carId } })
      );
      if (data) setValue("carBodyDamages", data);
    } catch (error) {
      showErrorAlert(TEXT.loadCarDamagesFailed);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (formInitial.id === undefined) loadActiveBodyDamages();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const carDamagesList = useMemo(() => {
    return watch("carBodyDamages") ? watch("carBodyDamages") : [];
  }, [watch]);

  const closeExpenseModal = useCallback(
    (response?: ExpenseModelActive) => {
      setIsExpenseModalOpen(false);
      nav.goBack();

      if (!response) return;

      const finalExpenseArray = [...getValues().expenses];

      if (response.delete) {
        if (typeof response.activeIndex === "number") {
          delete finalExpenseArray[response.activeIndex];
          setValue("expenses", finalExpenseArray.filter((n) => n));
        }
        return;
      }

      if (typeof response.activeIndex === "number") {
        finalExpenseArray[response.activeIndex] = response;
        setValue("expenses", finalExpenseArray);
        return;
      }

      finalExpenseArray.push(response);
      setValue("expenses", finalExpenseArray);
    },
    [getValues, setValue, nav]
  );

  const closeBodyDamageModal = useCallback(
    (response?: CarBodyDamageModel) => {
      setIsBodyDamageModalOpen(false);
      nav.goBack();

      if (!response) return;

      const finalcarBodyDamageArray = [...getValues().carBodyDamages];
      const indexFound = finalcarBodyDamageArray.findIndex(
        (bodyDamage) => bodyDamage.id === response.id
      );
      if (indexFound === -1) finalcarBodyDamageArray.push(response);
      else finalcarBodyDamageArray[indexFound] = response;

      setValue("carBodyDamages", finalcarBodyDamageArray);
    },
    [getValues, setValue, nav]
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
      showErrorAlert(TEXT.saveFailed);
    } finally {
      setIsLoading(false);
    }
  };

  const onDelete = async () => {
    if (!formInitial.id) return;
    setIsLoading(true);

    try {
      await api.delete(endpoints.INSPECTIONS_EDIT({ pathVariables: { id: formInitial.id } }));
      closeModal({ id: formInitial.id, delete: true });
    } catch (e) {
      showErrorAlert(TEXT.deleteFailed);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <IonPage id="car-inspection-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton color="danger" onClick={() => closeModal()}>
              {TEXT.cancel}
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.addCarInspection}</IonTitle>
          <IonButtons slot="end">
            <IonButton disabled={isLoading} strong onClick={handleSubmit(onSubmit)}>
              {TEXT.save}
            </IonButton>
          </IonButtons>
          {isLoading && <IonProgressBar type="indeterminate" />}
        </IonToolbar>
      </IonHeader>

      <IonContent>
        <form>
          <FormDate
            id="date-inspection-add"
            initialValue={String(watch("date") || "")}
            label={TEXT.date}
            presentation="date"
            formCallBack={(value: string) => setValue("date", value)}
          />

          <FormInput
            label={TEXT.driver}
            errorsObj={errors}
            errorName="driverName"
            initialValue={watch("driverName")}
            maxlength={50}
            changeCallback={(value: string) => setValue("driverName", value)}
            required
          />

          <FormInput
            label={TEXT.odometer}
            errorsObj={errors}
            errorName="odometer"
            initialValue={watch("odometer")}
            maxlength={15}
            type="number"
            changeCallback={(value: number) => setValue("odometer", value)}
            required
          />

          <FormSelect
            header={TEXT.cleaning}
            label={TEXT.intern}
            options={CLEANING}
            errorsObj={errors}
            errorName="internalCleaning"
            initialValue={watch("internalCleaning")}
            changeCallback={(value: string) => setValue("internalCleaning", value)}
            required
          />

          <FormSelect
            label={TEXT.extern}
            options={CLEANING}
            errorsObj={errors}
            errorName="externalCleaning"
            initialValue={watch("externalCleaning")}
            changeCallback={(value: string) => setValue("externalCleaning", value)}
            required
          />

          {/* pneus... (mantive como estava) */}
          <FormSelectFilterAdd
            header={`${TEXT.tire} ${TEXT.leftFront}`}
            label={TEXT.model}
            errorsObj={errors}
            errorName="leftFrontModel"
            formCallBack={(value: string) => setValue("leftFrontModel", value)}
            initialValue={watch("leftFrontModel")}
            options={TIRE_BRANDS}
            storageToken={TIRE_BRANDS_KEY}
            required
          />
          <FormSelect
            label={TEXT.integrity}
            options={INTEGRITY}
            errorsObj={errors}
            errorName="leftFrontIntegrity"
            initialValue={watch("leftFrontIntegrity")}
            changeCallback={(value: string) => setValue("leftFrontIntegrity", value)}
            required
          />

          <FormSelectFilterAdd
            header={`${TEXT.tire} ${TEXT.rightFront}`}
            label={TEXT.model}
            errorsObj={errors}
            errorName="rightFrontModel"
            formCallBack={(value: string) => setValue("rightFrontModel", value)}
            initialValue={watch("rightFrontModel")}
            options={TIRE_BRANDS}
            storageToken={TIRE_BRANDS_KEY}
            required
          />
          <FormSelect
            label={TEXT.integrity}
            options={INTEGRITY}
            errorsObj={errors}
            errorName="rightFrontIntegrity"
            initialValue={watch("rightFrontIntegrity")}
            changeCallback={(value: string) => setValue("rightFrontIntegrity", value)}
            required
          />

          <FormSelectFilterAdd
            header={`${TEXT.tire} ${TEXT.leftFBack}`}
            label={TEXT.model}
            errorsObj={errors}
            errorName="leftBackModel"
            formCallBack={(value: string) => setValue("leftBackModel", value)}
            initialValue={watch("leftBackModel")}
            options={TIRE_BRANDS}
            storageToken={TIRE_BRANDS_KEY}
            required
          />
          <FormSelect
            label={TEXT.integrity}
            options={INTEGRITY}
            errorsObj={errors}
            errorName="leftBackIntegrity"
            initialValue={watch("leftBackIntegrity")}
            changeCallback={(value: string) => setValue("leftBackIntegrity", value)}
            required
          />

          <FormSelectFilterAdd
            header={`${TEXT.tire} ${TEXT.rightFBack}`}
            label={TEXT.model}
            errorsObj={errors}
            errorName="rightBackModel"
            formCallBack={(value: string) => setValue("rightBackModel", value)}
            initialValue={watch("rightBackModel")}
            options={TIRE_BRANDS}
            storageToken={TIRE_BRANDS_KEY}
            required
          />
          <FormSelect
            label={TEXT.integrity}
            options={INTEGRITY}
            errorsObj={errors}
            errorName="rightBackIntegrity"
            initialValue={watch("rightBackIntegrity")}
            changeCallback={(value: string) => setValue("rightBackIntegrity", value)}
            required
          />

          <FormSelectFilterAdd
            header={`${TEXT.tire} ${TEXT.spare}`}
            label={TEXT.model}
            errorsObj={errors}
            errorName="spareModel"
            formCallBack={(value: string) => setValue("spareModel", value)}
            initialValue={watch("spareModel")}
            options={TIRE_BRANDS}
            storageToken={TIRE_BRANDS_KEY}
            required
          />
          <FormSelect
            label={TEXT.integrity}
            options={INTEGRITY}
            errorsObj={errors}
            errorName="spareIntegrity"
            initialValue={watch("spareIntegrity")}
            changeCallback={(value: string) => setValue("spareIntegrity", value)}
            required
          />

          <IonList>
            <IonListHeader>
              <IonLabel>
                <h1>{TEXT.inspectionsExpenses}</h1>
              </IonLabel>
              <IonButton
                onClick={(e) => {
                  e.preventDefault();
                  setActiveExpense({});
                  setIsExpenseModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true&modalExpenseOpened=true");
                }}
              >
                {TEXT.add}
              </IonButton>
            </IonListHeader>

            {watch("expenses")?.map((expense: InspectionExpenseModel, index) =>
              expense ? (
                <IonItem
                  button
                  key={index}
                  onClick={(e) => {
                    e.preventDefault();
                    setActiveExpense({ ...expense, activeIndex: index });
                    setIsExpenseModalOpen(true);
                    nav.push(nav.location.pathname + "?modalOpened=true&modalExpenseOpened=true");
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

            {(!watch("expenses") || watch("expenses").length === 0) && <ItemNotFound />}
          </IonList>

          <IonList>
            <IonListHeader>
              <IonLabel>
                <h1>{TEXT.carDamages}</h1>
              </IonLabel>
              <IonButton
                onClick={(e) => {
                  e.preventDefault();
                  setActiveCarBodyDamage({ date: watch("date"), responsible: watch("driverName") });
                  setIsBodyDamageModalOpen(true);
                  nav.push(nav.location.pathname + "?modalOpened=true&modalExpenseOpened=true");
                }}
              >
                {TEXT.add}
              </IonButton>
            </IonListHeader>

            {carDamagesList.map((carBodyDamage: CarBodyDamageModel, index) =>
              carBodyDamage ? (
                <IonItem
                  button
                  key={index}
                  onClick={(e) => {
                    e.preventDefault();
                    setActiveCarBodyDamage({ ...carBodyDamage });
                    setIsBodyDamageModalOpen(true);
                    nav.push(nav.location.pathname + "?modalOpened=true&modalExpenseOpened=true");
                  }}
                >
                  <BodyDamage carDamage={carBodyDamage} />
                </IonItem>
              ) : null
            )}

            {carDamagesList.length === 0 && <ItemNotFound />}
          </IonList>

          {formInitial.id && (
            <FormDeleteButton
              label={`${TEXT.delete} ${TEXT.inspection}`}
              message={TEXT.inspection}
              callBackFunc={onDelete}
            />
          )}
        </form>
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
