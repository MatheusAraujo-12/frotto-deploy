import {
  IonButton,
  IonButtons,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonPage,
  IonProgressBar,
  IonThumbnail,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import { camera, imagesOutline, warningOutline } from "ionicons/icons";
import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";

import { TEXT } from "../../../constants/texts";
import { CarBodyDamageModel } from "../../../constants/CarModels";
import {
  bodyDamageAddValidationSchema,
  initialBodyValues,
} from "./bodyDamageValidationSchema";

import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import { useAlert } from "../../../services/hooks/useAlert";
import { usePhotoGallery } from "../../../services/hooks/usePhotoGallery";
import { newFormDataFromBodyDamage } from "../../../services/formData";
import { urlToS3Image } from "../../../services/BodyImagePath";

import FormDate from "../../../components/Form/FormDate";
import FormInput from "../../../components/Form/FormInput";
import FormInputLabel from "../../../components/Form/FormInputLabel";
import FormToggle from "../../../components/Form/FormToggle";
import FormCurrency from "../../../components/Form/FormCurrency";
import FormSelectFilterAdd from "../../../components/Form/FormSelectFilterAdd";
import FrottoCard from "../../../components/UI/FrottoCard";

import { BODY_DAMAGES } from "../../../constants/selectOptions";
import { BODY_DAMAGE_KEY } from "../../../services/localStorage/localstorage";
import IonPhotoViewer from "@codesyntax/ionic-react-photo-viewer";
import { getApiErrorMessage } from "../../../services/apiErrorMessage";
import "./BodyDamageAdd.css";

interface CarDamageAddModalProps {
  closeModal: (response?: CarBodyDamageModel) => void;
  initialValues?: CarBodyDamageModel;
  carId: string; // ✅ string primitivo
}

const BodyDamageAdd: React.FC<CarDamageAddModalProps> = ({
  closeModal,
  initialValues,
  carId,
}) => {
  const { showErrorAlert } = useAlert();
  const { takePhoto } = usePhotoGallery();

  const [isLoading, setIsLoading] = useState(false);
  const [bodyFilePath, setBodyFilePath] = useState<string>();
  const [bodyFilePath2, setBodyFilePath2] = useState<string>();
  const [bodyFile, setBodyFile] = useState<File>();
  const [bodyFile2, setBodyFile2] = useState<File>();

  const formInitial = initialBodyValues(initialValues || {});

  const {
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<CarBodyDamageModel>({
    reValidateMode: "onBlur",
    resolver: yupResolver(bodyDamageAddValidationSchema),
    defaultValues: formInitial,
  });

  const updateField = useCallback(
    (field: keyof CarBodyDamageModel, value: any) => {
      setValue(field as any, value, {
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true,
      });
    },
    [setValue]
  );

  useEffect(() => {
    const nextValues = initialBodyValues(initialValues || {});
    reset(nextValues);
    setBodyFile(undefined);
    setBodyFile2(undefined);
    setBodyFilePath(
      nextValues.imagePath ? urlToS3Image(nextValues.imagePath) : undefined
    );
    setBodyFilePath2(
      nextValues.imagePath2 ? urlToS3Image(nextValues.imagePath2) : undefined
    );
  }, [initialValues, reset]);

  const takeBodyPhoto = async () => {
    const photo = await takePhoto(String(carId));
    if (!photo) return;
    setBodyFile(photo.file);
    setBodyFilePath(photo.path);
  };

  const takeBodyPhoto2 = async () => {
    const photo = await takePhoto(String(carId));
    if (!photo) return;
    setBodyFile2(photo.file);
    setBodyFilePath2(photo.path);
  };

  const onSubmit = useCallback(
    async (newCarDamage: CarBodyDamageModel) => {
      const formData = newFormDataFromBodyDamage(
        newCarDamage,
        bodyFile,
        bodyFile2
      );

      setIsLoading(true);

      try {
        let responseCar: CarBodyDamageModel;

        if (newCarDamage.id) {
          const urlPatch = endpoints.BODY_DAMAGE_EDIT({
            pathVariables: {
              id: String(newCarDamage.id), // ✅ string
            },
          });

          const response = await api.patch(urlPatch, formData, {
            headers: { "Content-Type": "multipart/form-data" },
          });

          responseCar = response.data;
        } else {
          const urlPost = endpoints.BODY_DAMAGE({
            pathVariables: {
              id: String(carId), // ✅ string
            },
          });

          const response = await api.post(urlPost, formData, {
            headers: { "Content-Type": "multipart/form-data" },
          });

          responseCar = response.data;
        }

        setIsLoading(false);
        closeModal(responseCar);
      } catch (error) {
        setIsLoading(false);
        showErrorAlert(getApiErrorMessage(error, TEXT.saveFailed));
      }
    },
    [bodyFile, bodyFile2, carId, closeModal, showErrorAlert]
  );

  const onInvalid = () => {
    showErrorAlert(TEXT.formHasErrors);
  };

  return (
    <IonPage id="car-body-damage-add-page">
      <IonHeader className="ion-no-border">
        <IonToolbar className="app-toolbar-clean">
          <IonButtons slot="start">
            <IonButton
              fill="clear"
              className="app-cancel-btn"
              onClick={() => closeModal()}
              disabled={isLoading}
            >
              {TEXT.cancel}
            </IonButton>
          </IonButtons>

          <IonTitle>{TEXT.addCarDamage}</IonTitle>

          <IonButtons slot="end">
            <IonButton
              className="app-save-btn"
              disabled={isLoading}
              onClick={handleSubmit(onSubmit, onInvalid)}
            >
              {TEXT.save}
            </IonButton>
          </IonButtons>

          {isLoading && <IonProgressBar type="indeterminate" />}
        </IonToolbar>
      </IonHeader>

      <IonContent className="body-damage-add-content">
        <div className="app-shell app-shell--compact body-damage-add-shell">
          <section className="app-section">
            <div className="body-damage-add-section-head">
              <h2 className="app-section-title">{TEXT.addCarDamage}</h2>
              <p className="app-section-subtitle">
                Registre a peça avariada, o custo e a responsabilidade.
              </p>
            </div>

            <FrottoCard>
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon app-soft-icon--warning">
                  <IonIcon icon={warningOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.addCarDamage}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Informe data, peça, custo e status da avaria.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <form
                  className="app-form-grid"
                  onSubmit={(e) => e.preventDefault()}
                >
                  <FormDate
                    id="date-car-damage"
                    label={TEXT.date}
                    presentation="date"
                    initialValue={watch("date") ?? ""}
                    errorsObj={errors}
                    errorName="date"
                    required
                    formCallBack={(value: string) => updateField("date", value)}
                  />

                  <FormSelectFilterAdd
                    label={TEXT.part}
                    errorsObj={errors}
                    errorName="part"
                    initialValue={watch("part") ?? ""}
                    options={BODY_DAMAGES}
                    storageToken={BODY_DAMAGE_KEY}
                    formCallBack={(value: string) => updateField("part", value)}
                    required
                  />

                  <FormCurrency
                    label={TEXT.cost}
                    errorsObj={errors}
                    errorName="cost"
                    initialValue={watch("cost") ?? ""}
                    maxlength={20}
                    changeCallback={(value: number) => updateField("cost", value)}
                    required
                  />

                  <FormInput
                    label={TEXT.responsible}
                    initialValue={watch("responsible") ?? ""}
                    maxlength={50}
                    changeCallback={(value: string) => updateField("responsible", value)}
                  />

                  <FormToggle
                    label={TEXT.resolved}
                    initialValue={watch("resolved") ?? false}
                    changeCallback={(value: boolean) => updateField("resolved", value)}
                  />
                </form>
              </IonCardContent>
            </FrottoCard>

            <FrottoCard>
              <IonCardHeader className="app-panel-header">
                <div className="app-soft-icon">
                  <IonIcon icon={imagesOutline} />
                </div>
                <div className="app-panel-header__content">
                  <IonCardTitle className="app-panel-title">
                    {TEXT.photo}
                  </IonCardTitle>
                  <IonCardSubtitle className="app-panel-subtitle">
                    Registre até duas fotos da avaria.
                  </IonCardSubtitle>
                </div>
              </IonCardHeader>
              <IonCardContent>
                <div className="body-damage-add-photos">
                  <IonItem className="app-form-item body-damage-add-photo-item">
                    {bodyFilePath && (
                      <IonThumbnail slot="start">
                        <IonPhotoViewer title={TEXT.photo} src={bodyFilePath}>
                          <img src={bodyFilePath} alt={TEXT.photo} />
                        </IonPhotoViewer>
                      </IonThumbnail>
                    )}

                    <FormInputLabel
                      name={bodyFilePath ? TEXT.photo : `${TEXT.add} ${TEXT.photo}`}
                    />

                    <IonButton
                      slot="end"
                      fill="outline"
                      className="app-outline-btn"
                      aria-label={`${TEXT.add} ${TEXT.photo}`}
                      onClick={takeBodyPhoto}
                    >
                      <IonIcon icon={camera} />
                    </IonButton>
                  </IonItem>

                  <IonItem className="app-form-item body-damage-add-photo-item">
                    {bodyFilePath2 && (
                      <IonThumbnail slot="start">
                        <IonPhotoViewer title={TEXT.photo2} src={bodyFilePath2}>
                          <img src={bodyFilePath2} alt={TEXT.photo2} />
                        </IonPhotoViewer>
                      </IonThumbnail>
                    )}

                    <FormInputLabel
                      name={bodyFilePath2 ? TEXT.photo2 : `${TEXT.add} ${TEXT.photo2}`}
                    />

                    <IonButton
                      slot="end"
                      fill="outline"
                      className="app-outline-btn"
                      aria-label={`${TEXT.add} ${TEXT.photo2}`}
                      onClick={takeBodyPhoto2}
                    >
                      <IonIcon icon={camera} />
                    </IonButton>
                  </IonItem>
                </div>
              </IonCardContent>
            </FrottoCard>
          </section>
        </div>
      </IonContent>
    </IonPage>
  );
};

export default BodyDamageAdd;
