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
import { TEXT } from "../../../constants/texts";
import { useCallback, useEffect, useState } from "react";
import { useAlert } from "../../../services/hooks/useAlert";
import { useForm } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import {
  bodyDamageAddValidationSchema,
  initialBodyValues,
} from "./bodyDamageValidationSchema";
import FormDate from "../../../components/Form/FormDate";
import FormInputLabel from "../../../components/Form/FormInputLabel";
import api from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import { CarBodyDamageModel } from "../../../constants/CarModels";
import { usePhotoGallery } from "../../../services/hooks/usePhotoGallery";
import { camera } from "ionicons/icons";
import { newFormDataFromBodyDamage } from "../../../services/formData";
import { urlToS3Image } from "../../../services/BodyImagePath";
import FormInput from "../../../components/Form/FormInput";
import FormToggle from "../../../components/Form/FormToggle";
import FormSelectFilterAdd from "../../../components/Form/FormSelectFilterAdd";
import { BODY_DAMAGES } from "../../../constants/selectOptions";
import { BODY_DAMAGE_KEY } from "../../../services/localStorage/localstorage";
import FormCurrency from "../../../components/Form/FormCurrency";
import IonPhotoViewer from "@codesyntax/ionic-react-photo-viewer";

interface CarDamageAddModalProps {
  closeModal: Function;
  initialValues?: CarBodyDamageModel;
  carId: String;
}

const BodyDamageAdd: React.FC<CarDamageAddModalProps> = ({
  closeModal,
  initialValues,
  carId,
}) => {
  const { showErrorAlert } = useAlert();
  const [isLoading, setisLoading] = useState(false);
  const [bodyFilePath, setBodyFilePath] = useState<string | undefined>(
    undefined
  );
  const [bodyFilePath2, setBodyFilePath2] = useState<string | undefined>(
    undefined
  );
  const [bodyFile, setBodyFile] = useState<File | undefined>(undefined);
  const [bodyFile2, setBodyFile2] = useState<File | undefined>(undefined);
  const formInitial = initialBodyValues(initialValues || {});
  const { takePhoto } = usePhotoGallery();

  const {
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm({
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
    const { file, path } = await takePhoto(carId);
    setBodyFilePath(path);
    setBodyFile(file);
  };

  const takeBodyPhoto2 = async () => {
    const { file, path } = await takePhoto(carId);
    setBodyFilePath2(path);
    setBodyFile2(file);
  };

  const onSubmit = useCallback(
    async (newCarDamage: CarBodyDamageModel) => {
      let formData = newFormDataFromBodyDamage(
        newCarDamage,
        bodyFile,
        bodyFile2
      );

      const headers = {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      };
      setisLoading(true);
      try {
        let responseCar: CarBodyDamageModel;
        if (newCarDamage.id) {
          const urlPatch = endpoints.BODY_DAMAGE_EDIT({
            pathVariables: {
              id: newCarDamage.id,
            },
          });
          const response = await api.patch(urlPatch, formData, headers);
          responseCar = response.data;
        } else {
          const urlPost = endpoints.BODY_DAMAGE({
            pathVariables: {
              id: carId,
            },
          });
          const response = await api.post(urlPost, formData, headers);
          responseCar = response.data;
        }
        setisLoading(false);
        closeModal(responseCar);
      } catch (e) {
        setisLoading(false);
        showErrorAlert(TEXT.saveFailed);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [bodyFile, bodyFile2]
  );

  return (
    <IonPage id="car-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton color="danger" onClick={() => closeModal()}>
              {TEXT.cancel}
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.addCarDamage}</IonTitle>
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
          <FormDate
            id="date-car-damage"
            initialValue={watch("date").toString()}
            label={TEXT.date}
            presentation="date"
            formCallBack={(value: string) => {
              setValue("date", value);
            }}
          />
          <FormSelectFilterAdd
            label={TEXT.part}
            errorsObj={errors}
            errorName="part"
            formCallBack={(value: string) => {
              setValue("part", value);
            }}
            initialValue={watch("part")}
            options={BODY_DAMAGES}
            storageToken={BODY_DAMAGE_KEY}
            required
          />
          <FormCurrency
            label={TEXT.cost}
            errorsObj={errors}
            errorName="cost"
            initialValue={watch("cost")}
            maxlength={20}
            changeCallback={(value: number) => {
              setValue("cost", value);
            }}
            required
          />
          <FormInput
            label={TEXT.responsible}
            initialValue={watch("responsible")}
            maxlength={50}
            changeCallback={(value: string) => {
              setValue("responsible", value);
            }}
          />
          <FormToggle
            label={TEXT.resolved}
            initialValue={watch("resolved")}
            changeCallback={(value: boolean) => {
              setValue("resolved", value);
            }}
          />
          <IonItem>
            {bodyFilePath && (
              <IonThumbnail slot="start">
                <IonPhotoViewer title={TEXT.photo} src={bodyFilePath}>
                  <img src={bodyFilePath} alt={TEXT.photo} />
                </IonPhotoViewer>
              </IonThumbnail>
            )}

            <FormInputLabel
              name={bodyFilePath ? TEXT.photo : TEXT.add + " " + TEXT.photo}
            />
            <IonButton
              shape="round"
              fill="outline"
              slot="end"
              onClick={() => takeBodyPhoto()}
            >
              <IonIcon slot="icon-only" icon={camera}></IonIcon>
            </IonButton>
          </IonItem>

          <IonItem>
            {bodyFilePath2 && (
              <IonThumbnail slot="start">
                <IonPhotoViewer title={TEXT.photo2} src={bodyFilePath2}>
                  <img src={bodyFilePath2} alt={TEXT.photo2} />
                </IonPhotoViewer>
              </IonThumbnail>
            )}

            <FormInputLabel
              name={bodyFilePath2 ? TEXT.photo2 : TEXT.add + " " + TEXT.photo2}
            />
            <IonButton
              shape="round"
              fill="outline"
              slot="end"
              onClick={() => takeBodyPhoto2()}
            >
              <IonIcon slot="icon-only" icon={camera}></IonIcon>
            </IonButton>
          </IonItem>
        </form>
      </IonContent>
    </IonPage>
  );
};

export default BodyDamageAdd;
