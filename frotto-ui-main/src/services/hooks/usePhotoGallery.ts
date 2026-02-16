import { TEXT } from "../../constants/texts";

type TakePhotoOptions = {
  // If true, prefer camera; otherwise, open gallery/files.
  preferCamera?: boolean;

  // If true, allows picking multiple files (only first is used here).
  multiple?: boolean;
};

type PickImageParams = {
  preferCamera: boolean;
  title?: string;
  multiple?: boolean;
};

type InternalPickedImage = {
  file: File;
  dataUrl?: string;
};

type CapacitorPhoto = {
  dataUrl?: string | null;
  webPath?: string | null;
  format?: string | null;
};

type CapacitorBridge = {
  isNativePlatform?: () => boolean;
  getPlatform?: () => string;
  convertFileSrc?: (path: string) => string;
  Plugins?: {
    Camera?: {
      getPhoto?: (options: Record<string, unknown>) => Promise<CapacitorPhoto>;
    };
  };
};

export type PickedImage = {
  file: File;
  previewUrl: string;
};

export function usePhotoGallery() {
  const pickImage = async (options: TakePhotoOptions = {}): Promise<PickedImage | null> => {
    const { preferCamera = false, multiple = false } = options;

    const picked = await pickImageFile({
      preferCamera,
      title: TEXT.photo,
      multiple,
    });

    if (!picked) {
      return null;
    }

    return {
      file: picked.file,
      previewUrl: createPreviewUrl(picked.file, picked.dataUrl),
    };
  };

  const takePhoto = async (carId: string, options: TakePhotoOptions = {}) => {
    const picked = await pickImage(options);
    if (!picked) {
      return null;
    }

    const ext = guessExtension(picked.file);
    const safeExt = ext ? `.${ext}` : "";
    const fileName = `Car_${carId}${safeExt}`;

    // Keeps file content and type while renaming.
    const renamed = new File([picked.file], fileName, { type: picked.file.type || "image/*" });
    const previewUrl = createPreviewUrl(renamed);

    return { file: renamed, path: previewUrl };
  };

  return { pickImage, takePhoto };
}

async function pickImageFile(params: PickImageParams): Promise<InternalPickedImage | null> {
  const nativeImage = await pickImageFileCapacitor(params);
  if (nativeImage !== undefined) {
    return nativeImage;
  }

  const webFile = await pickImageFileWeb(params);
  return webFile ? { file: webFile } : null;
}

async function pickImageFileCapacitor(params: PickImageParams): Promise<InternalPickedImage | null | undefined> {
  if (!isNativePlatform()) {
    return undefined;
  }

  const capacitor = getCapacitorBridge();
  const getPhoto = capacitor?.Plugins?.Camera?.getPhoto;
  if (!getPhoto) {
    return undefined;
  }

  try {
    const photo = await getPhoto({
      quality: 85,
      allowEditing: false,
      source: params.preferCamera ? "CAMERA" : "PHOTOS",
      resultType: "dataUrl",
      width: 1024,
      height: 1024,
      correctOrientation: true,
      saveToGallery: false,
      promptLabelHeader: params.title || TEXT.photo,
    });

    const dataUrl = `${photo?.dataUrl || ""}`.trim();
    if (dataUrl) {
      return {
        file: dataUrlToFile(dataUrl, photo?.format),
        dataUrl,
      };
    }

    const rawWebPath = `${photo?.webPath || ""}`.trim();
    if (!rawWebPath) {
      return null;
    }

    const webPath = capacitor?.convertFileSrc ? capacitor.convertFileSrc(rawWebPath) : rawWebPath;
    const response = await fetch(webPath);
    const blob = await response.blob();
    const extension = extensionFromMime(blob.type) || normalizeExtension(photo?.format) || "jpg";
    const fileName = `photo_${Date.now()}.${extension}`;
    const file = new File([blob], fileName, { type: blob.type || `image/${extension}` });

    return { file };
  } catch (error: any) {
    if (isUserCancellation(error)) {
      return null;
    }

    return undefined;
  }
}

async function pickImageFileWeb(params: PickImageParams) {
  return new Promise<File | null>((resolve) => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    input.multiple = !!params.multiple;

    // On mobile browsers this usually opens camera if supported.
    if (params.preferCamera) {
      (input as any).capture = "environment";
    }

    input.onchange = () => {
      const file = input.files?.[0] ?? null;
      resolve(file);
    };

    (input as any).oncancel = () => resolve(null);
    input.click();
  });
}

function guessExtension(file: File) {
  // Try file name first.
  const fromName = file.name?.split(".").pop()?.toLowerCase();
  if (fromName && fromName.length <= 5) return fromName;

  // Fallback to mime type.
  const type = (file.type || "").toLowerCase();
  if (type === "image/jpeg") return "jpg";
  if (type === "image/png") return "png";
  if (type === "image/webp") return "webp";
  if (type === "image/heic") return "heic";
  if (type === "image/heif") return "heif";
  return "";
}

function getCapacitorBridge(): CapacitorBridge | null {
  if (typeof window === "undefined") {
    return null;
  }
  return (window as any).Capacitor || null;
}

function isNativePlatform(): boolean {
  const capacitor = getCapacitorBridge();
  if (!capacitor) {
    return false;
  }

  if (typeof capacitor.isNativePlatform === "function") {
    return capacitor.isNativePlatform();
  }

  const platform = `${capacitor.getPlatform?.() || ""}`.toLowerCase();
  return platform === "android" || platform === "ios";
}

function dataUrlToFile(dataUrl: string, format?: string | null): File {
  const [header = "", payload = ""] = dataUrl.split(",");
  const mimeMatch = header.match(/data:([^;]+)/i);
  const mimeType = (mimeMatch?.[1] || mimeFromFormat(format) || "image/jpeg").toLowerCase();

  const binary = atob(payload);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }

  const extension = extensionFromMime(mimeType) || normalizeExtension(format) || "jpg";
  const fileName = `photo_${Date.now()}.${extension}`;
  return new File([bytes], fileName, { type: mimeType });
}

function extensionFromMime(mimeType?: string | null): string {
  const type = `${mimeType || ""}`.toLowerCase();
  if (type === "image/jpeg") return "jpg";
  if (type === "image/png") return "png";
  if (type === "image/webp") return "webp";
  if (type === "image/heic") return "heic";
  if (type === "image/heif") return "heif";
  return "";
}

function mimeFromFormat(format?: string | null): string {
  const normalized = normalizeExtension(format);
  if (!normalized) {
    return "";
  }

  if (normalized === "jpg") {
    return "image/jpeg";
  }

  return `image/${normalized}`;
}

function normalizeExtension(extension?: string | null): string {
  const normalized = `${extension || ""}`.toLowerCase().replace(/^\./, "");
  if (!normalized) {
    return "";
  }

  if (normalized === "jpeg") {
    return "jpg";
  }

  return normalized;
}

function createPreviewUrl(file: File, fallbackDataUrl?: string): string {
  try {
    return URL.createObjectURL(file);
  } catch (_error) {
    return fallbackDataUrl || "";
  }
}

function isUserCancellation(error: any): boolean {
  const message = `${error?.message || error || ""}`.toLowerCase();
  return message.includes("cancel");
}
