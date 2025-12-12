import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonPage,
  IonProgressBar,
  IonRange,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import { TEXT } from "../../../constants/texts";
import { useState } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import {
  driverAddValidationSchema,
  DriverForm,
  driverFormtoDriver,
  initialDriverValues,
} from "./DriverValidationSchema";
import FormDate from "../../../components/Form/FormDate";
import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import { CarDriverModel, DriverModel } from "../../../constants/CarModels";
import FormInput from "../../../components/Form/FormInput";
import FormSelect from "../../../components/Form/FormSelect";
import { STATES_BR } from "../../../constants/selectOptions";
import FormToggle from "../../../components/Form/FormToggle";
import FormCurrency from "../../../components/Form/FormCurrency";
import FormInputMask from "../../../components/Form/FormInputMask";
import FormInputLabel from "../../../components/Form/FormInputLabel";

interface DriverAddModalProps {
  closeModal: Function;
  initialValues?: CarDriverModel;
  carId: String;
}

const DriverAdd: React.FC<DriverAddModalProps> = ({
  closeModal,
  initialValues,
  carId,
}) => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const formInitial = initialDriverValues(initialValues || {});
  const [showConcluded, setShowConcluded] = useState(formInitial.concluded);

  const {
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm({
    reValidateMode: "onBlur",
    resolver: yupResolver(driverAddValidationSchema),
    defaultValues: formInitial,
  });

  const updateDriver = (driver: DriverModel) => {
    setValue("driverId", driver.id);
    setValue("driverName", driver.name ? driver.name : "");
    setValue("driverCpf", driver.cpf ? driver.cpf : "");
    setValue("driverEmail", driver.email ? driver.email : "");
    setValue("driverContact", driver.contact ? driver.contact : "");
    setValue(
      "driverEmergencyContact",
      driver.emergencyContact ? driver.emergencyContact : ""
    );
    setValue("driverPublicScore", driver.publicScore ? driver.publicScore : "");

    setValue("driverAddressId", driver.address?.id);
    setValue(
      "driverAddressCountry",
      driver.address?.country ? driver.address?.country : ""
    );
    setValue(
      "driverAddressZip",
      driver.address?.zip ? driver.address?.zip : ""
    );
    setValue(
      "driverAddressState",
      driver.address?.state ? driver.address?.state : ""
    );
    setValue(
      "driverAddressCity",
      driver.address?.city ? driver.address?.city : ""
    );
    setValue(
      "driverAddressDistrict",
      driver.address?.district ? driver.address?.district : ""
    );
    setValue(
      "driverAddressName",
      driver.address?.name ? driver.address?.name : ""
    );
  };

  const loadDriverByCpf = async (cpf: string | undefined | null | number) => {
    if (typeof cpf === "string" && cpf.length === 11) {
      setisLoading(true);
      try {
        const urlGet = endpoints.DRIVER_CPF({
          pathVariables: {
            cpf: cpf,
          },
        });
        const { data } = await api.get(urlGet);
        if (data) {
          updateDriver(data);
        } else {
          setValue("driverId", undefined);
          setValue("driverAddressId", undefined);
        }
        setisLoading(false);
      } catch (e) {
        setisLoading(false);
        setValue("driverId", undefined);
        setValue("driverAddressId", undefined);
      }
    }
  };

  const onSubmit = async (newCarDriverForm: DriverForm) => {
    const newCarDriver = driverFormtoDriver(newCarDriverForm);
    setisLoading(true);
    try {
      let responseCar: CarDriverModel;
      if (newCarDriver.id) {
        const urlPatch = endpoints.DRIVERS_EDIT({
          pathVariables: {
            id: newCarDriver.id,
          },
        });
        const response = await api.put(urlPatch, newCarDriver);
        responseCar = response.data;
      } else {
        const urlPost = endpoints.DRIVERS({
          pathVariables: {
            id: carId,
          },
        });
        const response = await api.post(urlPost, newCarDriver);
        responseCar = response.data;
      }
      setisLoading(false);
      closeModal(responseCar);
    } catch (e: any) {
      setisLoading(false);
      if (e?.response?.data?.errorKey === "activedriverexists") {
        showErrorAlert(TEXT.activeDriverExist);
      } else {
        showErrorAlert(TEXT.saveFailed);
      }
    }
  };

  return (
    <IonPage id="car-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton color="danger" onClick={() => closeModal()}>
              {TEXT.cancel}
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.addCarDriver}</IonTitle>
          <IonButtons slot="end">
            <IonButton
              disabled={isLoading}
              strong={true}
              onClick={handleSubmit(onSubmit)}
            >
              {TEXT.save}
            </IonButton>
          </IonButtons>
          {isLoading && <IonProgressBar type="indeterminate"></IonProgressBar>}
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <form>
          <IonItem>
            <IonLabel>
              <h1>{TEXT.driverContract}</h1>
            </IonLabel>
          </IonItem>
          <FormDate
            id="start-date-driver-add"
            initialValue={watch("startDate").toString()}
            label={TEXT.dateStart}
            presentation="date"
            formCallBack={(value: string) => {
              setValue("startDate", value);
            }}
          />
          <FormCurrency
            label={TEXT.warranty}
            errorsObj={errors}
            errorName="warranty"
            initialValue={watch("warranty")}
            maxlength={20}
            changeCallback={(value: number) => {
              setValue("warranty", value);
            }}
            required
          />
          <FormToggle
            label={TEXT.resolved}
            initialValue={watch("concluded")}
            changeCallback={(value: boolean) => {
              setValue("concluded", value);
              setShowConcluded(value);
            }}
          />

          {showConcluded && (
            <>
              <FormDate
                id="end-date-driver-add"
                initialValue={watch("endDate").toString()}
                label={TEXT.dateEnd}
                presentation="date"
                formCallBack={(value: string) => {
                  setValue("endDate", value);
                }}
              />
              <FormCurrency
                label={TEXT.debt}
                errorsObj={errors}
                errorName="debt"
                initialValue={watch("debt")}
                maxlength={20}
                changeCallback={(value: number) => {
                  setValue("debt", value);
                }}
                required
              />
              <IonItem>
                <FormInputLabel name={TEXT.score}></FormInputLabel>
                <IonRange
                  value={watch("score")}
                  pin={true}
                  ticks={true}
                  snaps={true}
                  min={1}
                  max={5}
                  onIonKnobMoveEnd={({ detail }) => {
                    setValue("score", +detail.value);
                  }}
                ></IonRange>
              </IonItem>
            </>
          )}
          <IonItem>
            <IonLabel>
              <h1>{TEXT.driver}</h1>
            </IonLabel>
          </IonItem>
          <FormInputMask
            label={TEXT.cpf}
            errorsObj={errors}
            errorName="driverCpf"
            initialValue={watch("driverCpf")}
            inputmode="numeric"
            maxlength={50}
            changeCallback={(value: string) => {
              setValue("driverCpf", value);
              loadDriverByCpf(value);
            }}
            required
          />
          <FormInput
            label={TEXT.name}
            errorsObj={errors}
            errorName="driverName"
            initialValue={watch("driverName")}
            maxlength={50}
            changeCallback={(value: string) => {
              setValue("driverName", value);
            }}
            required
          />
          <FormInputMask
            label={TEXT.contact}
            errorsObj={errors}
            errorName="driverContact"
            initialValue={watch("driverContact")}
            inputmode="tel"
            maskOptions={{
              mask: "(00) 00000-00000",
            }}
            changeCallback={(value: string) => {
              setValue("driverContact", value);
            }}
            required
          />
          <FormInput
            label={TEXT.email}
            errorsObj={errors}
            errorName="driverEmail"
            type="email"
            initialValue={watch("driverEmail")}
            maxlength={50}
            changeCallback={(value: string) => {
              setValue("driverEmail", value);
            }}
          />
          <FormInputMask
            label={TEXT.emergencyContact}
            errorsObj={errors}
            errorName="driverEmergencyContact"
            initialValue={watch("driverEmergencyContact")}
            inputmode="tel"
            maskOptions={{
              mask: "(00) 00000-00000",
            }}
            changeCallback={(value: string) => {
              setValue("driverEmergencyContact", value);
            }}
          />
          <FormInputMask
            label={TEXT.emergencyContact}
            errorsObj={errors}
            errorName="driverEmergencyContactSecond"
            initialValue={watch("driverEmergencyContactSecond")}
            inputmode="tel"
            maskOptions={{
              mask: "(00) 00000-00000",
            }}
            changeCallback={(value: string) => {
              setValue("driverEmergencyContactSecond", value);
            }}
          />
          <FormInput
            label={TEXT.documentLicense}
            errorsObj={errors}
            errorName="driverDocumentDriverLicense"
            initialValue={watch("driverDocumentDriverLicense")}
            maxlength={30}
            changeCallback={(value: string) => {
              setValue("driverDocumentDriverLicense", value);
            }}
          />
          <FormInput
            label={TEXT.documentRegister}
            errorsObj={errors}
            errorName="driverDocumentDriverRegister"
            initialValue={watch("driverDocumentDriverRegister")}
            maxlength={30}
            changeCallback={(value: string) => {
              setValue("driverDocumentDriverRegister", value);
            }}
          />
          <FormInput
            label={TEXT.publicScore}
            errorsObj={errors}
            errorName="driverPublicScore"
            type="number"
            initialValue={watch("driverPublicScore")}
            maxlength={20}
            changeCallback={(value: string) => {
              setValue("driverPublicScore", value);
            }}
          />
          <FormInput
            label={TEXT.country}
            errorsObj={errors}
            errorName="driverAddressCountry"
            initialValue={watch("driverAddressCountry")}
            maxlength={20}
            changeCallback={(value: string) => {
              setValue("driverAddressCountry", value);
            }}
            required
          />
          <FormInputMask
            label={TEXT.zip}
            errorsObj={errors}
            errorName="driverAddressZip"
            initialValue={watch("driverAddressZip")}
            inputmode="numeric"
            maskOptions={{
              mask: "00.000-000",
            }}
            changeCallback={(value: string) => {
              setValue("driverAddressZip", value);
            }}
            required
          />
          <FormSelect
            label={TEXT.state}
            options={STATES_BR}
            errorsObj={errors}
            errorName="driverAddressState"
            initialValue={watch("driverAddressState")}
            changeCallback={(value: string) => {
              setValue("driverAddressState", value);
            }}
            required
          />
          <FormInput
            label={TEXT.city}
            errorsObj={errors}
            errorName="driverAddressCity"
            initialValue={watch("driverAddressCity")}
            maxlength={20}
            changeCallback={(value: string) => {
              setValue("driverAddressCity", value);
            }}
            required
          />
          <FormInput
            label={TEXT.district}
            errorsObj={errors}
            errorName="driverAddressDistrict"
            initialValue={watch("driverAddressDistrict")}
            maxlength={30}
            changeCallback={(value: string) => {
              setValue("driverAddressDistrict", value);
            }}
            required
          />
          <FormInput
            label={TEXT.address}
            errorsObj={errors}
            errorName="driverAddressName"
            initialValue={watch("driverAddressName")}
            maxlength={50}
            changeCallback={(value: string) => {
              setValue("driverAddressName", value);
            }}
            required
          />
        </form>
      </IonContent>
    </IonPage>
  );
};

export default DriverAdd;
