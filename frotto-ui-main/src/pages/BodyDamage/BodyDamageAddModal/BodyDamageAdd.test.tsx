import { render, waitFor } from "@testing-library/react";
import { IonApp } from "@ionic/react";
import BodyDamageAdd from "./BodyDamageAdd";
import api from "../../../services/axios/axios";

jest.mock("../../../services/axios/axios");
jest.mock("@codesyntax/ionic-react-photo-viewer", () => ({ children }: any) => children);

const mockedApi = api as jest.Mocked<typeof api>;
const SIGNED_1 = "https://api-staging.frotto.test/files/car-damages/2026/10/a.png?exp=1&sig=s";
const SIGNED_2 = "https://api-staging.frotto.test/files/car-damages/2026/10/b.jpg?exp=1&sig=s";

const renderModal = (initialValues: any) => {
  const view = render(
    <IonApp>
      <BodyDamageAdd closeModal={jest.fn()} initialValues={initialValues} carId="11" />
    </IonApp>
  );
  const photos = () => Array.from(view.container.querySelectorAll("img")).map((img) => img.getAttribute("src"));
  return { ...view, photos };
};

describe("BodyDamageAdd - fotos resolvidas pelo backend (Etapa 4 storage)", () => {
  beforeAll(() => {
    // jsdom has no IntersectionObserver (used by ion-datetime-button).
    (global as any).IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });

  beforeEach(() => {
    jest.resetAllMocks();
  });

  it("shows the URLs resolved by the damage endpoints", async () => {
    const { photos } = renderModal({
      id: 31,
      part: "Porta",
      imagePath: "car-damages/2026/10/a.png",
      imagePath2: "car-damages/2026/10/b.jpg",
      imageUrl: SIGNED_1,
      imageUrl2: SIGNED_2,
    });

    await waitFor(() => expect(photos()).toEqual([SIGNED_1, SIGNED_2]));
    expect(mockedApi.get).not.toHaveBeenCalled();
  });

  it("shows no photo when the backend resolves none, without building an S3 URL from the key", async () => {
    const { photos } = renderModal({ id: 31, part: "Porta", imagePath: "1672926360659_Car_11.png", imageUrl: "", imageUrl2: "" });

    await waitFor(() => expect(photos()).toEqual([]));
    expect(mockedApi.get).not.toHaveBeenCalled();
  });

  it("damages opened from an inspection ask the damage endpoint for their URLs", async () => {
    mockedApi.get.mockResolvedValue({ data: { id: 31, imagePath: "car-damages/2026/10/a.png", imageUrl: SIGNED_1, imageUrl2: "" } });

    const { photos } = renderModal({ id: 31, part: "Porta", imagePath: "car-damages/2026/10/a.png" });

    await waitFor(() => expect(photos()).toEqual([SIGNED_1]));
    expect(mockedApi.get).toHaveBeenCalledWith(expect.stringContaining("/car-body-damages/31"));
    expect(photos().some((src) => `${src}`.includes("amazonaws"))).toBe(false);
  });

  it("falls back to the legacy bucket URL when the damage endpoint is unreachable", async () => {
    mockedApi.get.mockRejectedValue(new Error("network"));

    const { photos } = renderModal({ id: 31, part: "Porta", imagePath: "1672926360659_Car_11.png" });

    await waitFor(() => expect(photos()).toHaveLength(1));
    expect(photos()[0]).toContain("1672926360659_Car_11.png");
  });

  it("new damages without photos make no request", async () => {
    const { photos } = renderModal({});

    await waitFor(() => expect(photos()).toEqual([]));
    expect(mockedApi.get).not.toHaveBeenCalled();
  });
});
