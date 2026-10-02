import {
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonIcon,
  useIonRouter,
} from "@ionic/react";
import { carSportOutline } from "ionicons/icons";
import { TEXT } from "../../../constants/texts";
import { useState } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import {
  carAddValidationSchema,
  initialCarValues,
} from "./carAddValidationSchema";
import FormDate from "../../../components/Form/FormDate";
import api from "../../../services/axios/axios";
import { getApiErrorMessage } from "../../../services/apiErrorMessage";
import endpoints from "../../../constants/endpoints";
import {
  CarAdminStatus,
  CarModel,
  CommissionType,
} from "../../../constants/CarModels";
import {
  CAR_ADMIN_STATUS_OPTIONS,
  COLORS,
  COMMISSION_TYPES,
} from "../../../constants/selectOptions";
import FormInput from "../../../components/Form/FormInput";
import FormSelect from "../../../components/Form/FormSelect";
import FormDeleteButton from "../../../components/Form/FormDeleteButton";
import FormCurrency from "../../../components/Form/FormCurrency";
import FrottoCard from "../../../components/UI/FrottoCard";
import FrottoModal from "../../../components/UI/FrottoModal";
import {
  buildCarLegacyName,
  resolveCarIdentity,
} from "../../../components/Car/carIdentity";

const VEHICLE_LIMIT_REACHED_MESSAGE =
  "Você atingiu o limite de veículos do seu plano. Faça upgrade para cadastrar outro veículo.";

interface CarAddModalProps {
  closeModal: (response?: CarModel) => void;
  initialValues?: CarModel;
}

const CarAdd: React.FC<CarAddModalProps> = ({ closeModal, initialValues }) => {
  const history = useIonRouter();
  const { showErrorAlert, showSuccessAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const formInitial = initialCarValues(initialValues || {});
  const {
    setValue,
    watch,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(carAddValidationSchema),
    defaultValues: formInitial,
  });

  const onSubmit = async (newCar: CarModel) => {
    const identity = resolveCarIdentity(newCar);
    const payload: CarModel = {
      ...newCar,
      name: buildCarLegacyName({
        ...newCar,
        brand: identity.brand,
        model: identity.model,
      }),
      brand: identity.brand,
      model: identity.model,
    };

    setisLoading(true);
    try {
      let responseCar: CarModel;
      if (payload.id) {
        const response = await api.patch(
          endpoints.CAR({
            pathVariables: {
              id: payload.id,
            },
          }),
          payload
        );
        responseCar = response.data;
      } else {
        const response = await api.post(endpoints.CARS(), {
          ...payload,
          active: true,
        });
        responseCar = response.data;
      }
      if (!payload.id) {
        const { fleetBillingFeedback } = await import("../../../services/fleetBillingFeedback");
        showSuccessAlert?.(await fleetBillingFeedback("added"));
      }
      setisLoading(false);
      closeModal(responseCar);
    } catch (e) {
      setisLoading(false);
      showErrorAlert(
        getApiErrorMessage(e, TEXT.saveFailed, {
          VEHICLE_LIMIT_REACHED: VEHICLE_LIMIT_REACHED_MESSAGE,
        })
      );
    }
  };

  const onDelete = async () => {
    setisLoading(true);
    try {
      await api.delete(
        endpoints.CAR({ pathVariables: { id: formInitial.id } })
      );
      const { fleetBillingFeedback } = await import("../../../services/fleetBillingFeedback");
      showSuccessAlert?.(await fleetBillingFeedback("deleted"));
      history.push("/", "none", "replace");
      setisLoading(false);
    } catch (e) {
      setisLoading(false);
      showErrorAlert(TEXT.deleteFailed);
    }
  };

  return (
    <FrottoModal
      pageId="car-add-page"
      title={formInitial.id ? TEXT.editCar : TEXT.newCar}
      onCancel={() => closeModal()}
      cancelVariant="danger"
      primaryVariant="save"
      primaryLabel={TEXT.save}
      onPrimaryAction={handleSubmit(onSubmit)}
      primaryDisabled={isLoading}
      isLoading={isLoading}
    >
        <div className="app-shell app-shell--compact">
          <section className="app-section">
            <FrottoCard>
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={carSportOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.addCar}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Informe os dados do veículo.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <form
                  className="app-form-grid"
                  onSubmit={(e) => e.preventDefault()}
                >
                  <FormInput
                    label="Marca"
                    errorsObj={errors}
                    errorName="brand"
                    initialValue={watch("brand") ?? ""}
                    maxlength={60}
                    changeCallback={(value: string) => {
                      setValue("brand", value, {
                        shouldValidate: true,
                        shouldDirty: true,
                        shouldTouch: true,
                      });
                    }}
                  />
                  <FormInput
                    label={TEXT.model}
                    errorsObj={errors}
                    errorName="model"
                    initialValue={watch("model") ?? ""}
                    maxlength={60}
                    changeCallback={(value: string) => {
                      setValue("model", value, {
                        shouldValidate: true,
                        shouldDirty: true,
                        shouldTouch: true,
                      });
                    }}
                  />
                  <FormInput
                    label={TEXT.plate}
                    errorsObj={errors}
                    errorName="plate"
                    initialValue={watch("plate")}
                    maxlength={15}
                    changeCallback={(value: string) => {
                      setValue("plate", value);
                    }}
                    required
                  />
                  <FormInput
                    label={TEXT.odometer}
                    errorsObj={errors}
                    errorName="odometer"
                    initialValue={watch("odometer")}
                    maxlength={15}
                    type="number"
                    changeCallback={(value: number) => {
                      setValue("odometer", value);
                    }}
                    required
                  />
                  <FormInput
                    label={TEXT.group}
                    errorsObj={errors}
                    errorName="group"
                    initialValue={watch("group")}
                    maxlength={20}
                    changeCallback={(value: string) => {
                      setValue("group", value);
                    }}
                    required
                  />
                  <FormSelect
                    label={TEXT.adminStatus}
                    options={CAR_ADMIN_STATUS_OPTIONS}
                    errorsObj={errors}
                    errorName="adminStatus"
                    initialValue={watch("adminStatus")}
                    changeCallback={(value: string) => {
                      setValue("adminStatus", value as CarAdminStatus);
                    }}
                    required
                  />
                  <FormSelect
                    label={TEXT.color}
                    options={COLORS}
                    errorsObj={errors}
                    errorName="color"
                    initialValue={watch("color")}
                    changeCallback={(value: string) => {
                      setValue("color", value);
                    }}
                    required
                  />
                  <FormSelect
                    label={TEXT.commissionType}
                    options={COMMISSION_TYPES}
                    errorsObj={errors}
                    errorName="commissionType"
                    initialValue={watch("commissionType")}
                    changeCallback={(value: string) => {
                      setValue("commissionType", value as CommissionType);
                    }}
                    required
                  />
                  {watch("commissionType") === "PERCENT_PROFIT" && (
                    <FormInput
                      label={TEXT.commissionPercent}
                      errorsObj={errors}
                      errorName="commissionPercent"
                      initialValue={watch("commissionPercent")}
                      type="number"
                      changeCallback={(value: string) => {
                        const parsed = Number(String(value).replace(",", "."));
                        setValue(
                          "commissionPercent",
                          Number.isNaN(parsed) ? 0 : parsed
                        );
                      }}
                      required
                    />
                  )}
                  {watch("commissionType") === "FIXED" && (
                    <FormCurrency
                      label={TEXT.commissionFixed}
                      errorsObj={errors}
                      errorName="commissionFixed"
                      initialValue={watch("commissionFixed")}
                      maxlength={15}
                      changeCallback={(value: number) => {
                        setValue("commissionFixed", value);
                      }}
                      required
                    />
                  )}
                  <FormCurrency
                    label={TEXT.initialValue}
                    errorsObj={errors}
                    errorName="initialValue"
                    initialValue={watch("initialValue")}
                    maxlength={15}
                    changeCallback={(value: number) => {
                      setValue("initialValue", value);
                    }}
                  />
                  <FormDate
                    id="year-car-add"
                    initialValue={watch("year").toString()}
                    label={TEXT.year}
                    presentation="year"
                    formCallBack={(value: string) => {
                      setValue("year", Number(value));
                    }}
                  />
                </form>
              </IonCardContent>
            </FrottoCard>

            {formInitial.id && (
              <div>
                <FormDeleteButton
                  label={`${TEXT.delete} ${TEXT.car}`}
                  message={TEXT.car}
                  callBackFunc={onDelete}
                />
              </div>
            )}
          </section>
        </div>
    </FrottoModal>
  );
};

export default CarAdd;
