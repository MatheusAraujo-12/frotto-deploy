import { Camera, CameraResultType, CameraSource } from "@capacitor/camera";
import { TEXT } from "../../constants/texts";

const createFile = async (
  path: string,
  name: string,
  type: string
): Promise<File> => {
  let response = await fetch(path);
  let data = await response.blob();
  let metadata = {
    type: type,
  };
  return new File([data], name, metadata);
};

export function usePhotoGallery() {
  const takePhoto = async (carId: String) => {
    const photo = await Camera.getPhoto({
      resultType: CameraResultType.Uri,
      source: CameraSource.Prompt,
      promptLabelHeader: TEXT.photo,
      promptLabelCancel: TEXT.cancel,
      promptLabelPhoto: TEXT.gallery,
      promptLabelPicture: TEXT.takePicture,
      quality: 100,
      width: 700,
      height: 700,
    });
    const fileName = "Car_" + carId + ".png";
    const filePhoto: File = await createFile(
      photo.webPath!,
      fileName,
      "image/png"
    );
    return { file: filePhoto, path: photo.webPath };
  };

  return {
    takePhoto,
  };
}
