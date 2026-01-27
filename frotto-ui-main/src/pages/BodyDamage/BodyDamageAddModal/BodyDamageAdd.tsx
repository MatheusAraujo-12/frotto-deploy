import {
  IonButton,
  IonButtons,
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
import { camera } from "ionicons/icons";
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

import { BODY_DAMAGES } from "../../../constants/selectOptions";
import { BODY_DAMAGE_KEY } from "../../../services/localStorage/localstorage";
import IonPhotoViewer from "@codesyntax/ionic-react-photo-viewer";

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
    formState: { errors },
  } = useForm<CarBodyDamageModel>({
    reValidateMode: "onBlur",
    resolver: yupResolver(bodyDamageAddValidationSchema),
    defaultValues: formInitial,
  });

  useEffect(() => {
    if (formInitial.imagePath) {
      setBodyFilePath(urlToS3Image(formInitial.imagePath));
    }
    if (formInitial.imagePath2) {
      setBodyFilePath2(urlToS3Image(formInitial.imagePath2));
    }
  }, [formInitial.imagePath, formInitial.imagePath2]);

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
      } catch (error: any) {
        setIsLoading(false);
        const errorMessage =
          error?.response?.data?.message ||
          error?.response?.data?.detail ||
          error?.message ||
          TEXT.saveFailed;
        showErrorAlert(errorMessage);
      }
    },
    [bodyFile, bodyFile2, carId, closeModal, showErrorAlert]
  );

  return (
    <IonPage id="car-body-damage-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton color="medium" onClick={() => closeModal()}>
              {TEXT.cancel}
            </IonButton>
          </IonButtons>

          <IonTitle>{TEXT.addCarDamage}</IonTitle>

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
            id="date-car-damage"
            label={TEXT.date}
            presentation="date"
            initialValue={watch("date") ?? ""}
            formCallBack={(value: string) => setValue("date", value)}
          />

          <FormSelectFilterAdd
            label={TEXT.part}
            errorsObj={errors}
            errorName="part"
            initialValue={watch("part") ?? ""}
            options={BODY_DAMAGES}
            storageToken={BODY_DAMAGE_KEY}
            formCallBack={(value: string) => setValue("part", value)}
            required
          />

          <FormCurrency
            label={TEXT.cost}
            errorsObj={errors}
            errorName="cost"
            initialValue={watch("cost") ?? ""}
            maxlength={20}
            changeCallback={(value: number) => setValue("cost", value)}
            required
          />

          <FormInput
            label={TEXT.responsible}
            initialValue={watch("responsible") ?? ""}
            maxlength={50}
            changeCallback={(value: string) => setValue("responsible", value)}
          />

          <FormToggle
            label={TEXT.resolved}
            initialValue={watch("resolved") ?? false}
            changeCallback={(value: boolean) => setValue("resolved", value)}
          />

          {/* Foto 1 */}
          <IonItem>
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

            <IonButton slot="end" fill="outline" onClick={takeBodyPhoto}>
              <IonIcon icon={camera} />
            </IonButton>
          </IonItem>

          {/* Foto 2 */}
          <IonItem>
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

            <IonButton slot="end" fill="outline" onClick={takeBodyPhoto2}>
              <IonIcon icon={camera} />
            </IonButton>
          </IonItem>
        </form>
      </IonContent>
    </IonPage>
  );
};

export default BodyDamageAdd;
