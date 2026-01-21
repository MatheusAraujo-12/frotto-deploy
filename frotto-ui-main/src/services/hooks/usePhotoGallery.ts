import { TEXT } from "../../constants/texts";

type TakePhotoOptions = {
  /**
   * Se true, em celular tenta abrir diretamente a câmera (quando suportado).
   * Se false, abre galeria/arquivos.
   */
  preferCamera?: boolean;

  /**
   * Se true, limita para uma imagem; se false, permite múltiplas (padrão: false)
   */
  multiple?: boolean;
};

export function usePhotoGallery() {
  const takePhoto = async (carId: string, options: TakePhotoOptions = {}) => {
    const { preferCamera = false, multiple = false } = options;

    const file = await pickImageFileWeb({
      preferCamera,
      title: TEXT.photo,
      multiple,
    });

    if (!file) {
      // usuário cancelou
      return null;
    }

    const ext = guessExtension(file);
    const safeExt = ext ? `.${ext}` : "";
    const fileName = `Car_${carId}${safeExt}`;

    // Renomeia mantendo o conteúdo e o tipo real
    const renamed = new File([file], fileName, { type: file.type || "image/*" });

    // Para preview no browser (bem mais leve que base64)
    const objectUrl = URL.createObjectURL(renamed);

    return { file: renamed, path: objectUrl };
  };

  return { takePhoto };
}

async function pickImageFileWeb(params: {
  preferCamera: boolean;
  title?: string;
  multiple?: boolean;
}) {
  return new Promise<File | null>((resolve) => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    input.multiple = !!params.multiple;

    // Em mobile, isso costuma abrir câmera se suportado
    if (params.preferCamera) {
      (input as any).capture = "environment";
    }

    input.onchange = () => {
      const file = input.files?.[0] ?? null;
      resolve(file);
    };

    input.click();
  });
}

function guessExtension(file: File) {
  // tenta pelo nome original
  const fromName = file.name?.split(".").pop()?.toLowerCase();
  if (fromName && fromName.length <= 5) return fromName;

  // tenta pelo mime
  const type = (file.type || "").toLowerCase();
  if (type === "image/jpeg") return "jpg";
  if (type === "image/png") return "png";
  if (type === "image/webp") return "webp";
  if (type === "image/heic") return "heic";
  return "";
}
