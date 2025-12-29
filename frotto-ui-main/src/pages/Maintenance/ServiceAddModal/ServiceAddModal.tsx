import {
  IonButton,
  IonButtons,
  IonCheckbox,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonPage,
  IonSearchbar,
  IonText,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import { TEXT } from "../../../constants/texts";

import { SERVICES } from "../../../constants/selectOptions";
import {
  addOneToArray,
  getArrays,
  SERVICES_KEY,
} from "../../../services/localStorage/localstorage";

import { MaintenanceServiceModel } from "../../../constants/CarModels";
import { useCallback, useEffect, useMemo, useState } from "react";
import { filterObject } from "../../../services/filterList";
import ItemNotFound from "../../../components/List/ItemNotFound";
import {
  currencyFormat,
  updateNumberByKeyandPrevious,
} from "../../../services/currencyFormat";
import { CurrencyInput } from "../../../components/Form/FormCurrency";

interface ServiceObj {
  [k: string]: SelectedMaintenanceServiceModel;
}
interface SelectedMaintenanceServiceModel extends MaintenanceServiceModel {
  selected?: boolean;
}
interface ServiceAddModalProps {
  closeModal: (response?: MaintenanceServiceModel[]) => void;
  initialValues: MaintenanceServiceModel[];
}

const ServiceAddModal: React.FC<ServiceAddModalProps> = ({
  closeModal,
  initialValues,
}) => {
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [storageOptions, setStorageOptions] = useState<string[]>([]);
  const [servicesList, setServicesList] = useState<ServiceObj>({});

  useEffect(() => {
    loadStorageOptions();
  }, []);

  const loadStorageOptions = () => {
    if (SERVICES_KEY) {
      setStorageOptions(getArrays(SERVICES_KEY));
    }
  };

  const addToList = useCallback(
    (newItem: string | undefined) => {
      if (SERVICES_KEY && newItem && newItem !== "") {
        addOneToArray(newItem, SERVICES_KEY);
        const finalServiceList :ServiceObj = {...servicesList};
        finalServiceList[newItem] = {
          id: undefined,
          cost: 0,
          name: newItem,
          selected: false,
        };
        setServicesList(finalServiceList)
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [setServicesList, servicesList]
  );

  useEffect(() => {
    const finalOptions = [...SERVICES, ...storageOptions];
    const finalServiceList: ServiceObj = {};
    finalOptions.forEach((option) => {
      finalServiceList[option] = {
        id: undefined,
        cost: 0,
        name: option,
        selected: false,
      };
    });
    initialValues.forEach((option) => {
      if (option.name) {
        finalServiceList[option.name] = {
          id: option.id,
          cost: option.cost,
          name: option.name,
          selected: true,
        };
      }
    });
    setServicesList(finalServiceList);
  }, [storageOptions, initialValues]);

  const filteredList = useMemo(() => {
    if (searchValue && searchValue !== "") {
      const lowerCaseSearch = searchValue.toLowerCase();
      return filterObject(servicesList, ([k, _]) =>
        k.toLocaleLowerCase().includes(lowerCaseSearch)
      );
    }
    return servicesList;
  }, [servicesList, searchValue]);

  const save = useCallback(() => {
    const selectedList = filterObject(
      servicesList,
      ([_, v]) => v?.selected === true
    );
    const selectedServices = Object.values(selectedList).filter(
      (service): service is MaintenanceServiceModel => service !== undefined
    );
    closeModal(selectedServices);
  }, [servicesList, closeModal]);

  return (
    <IonPage id="services-add-page">
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonButton color="danger" onClick={() => closeModal()}>
              {TEXT.cancel}
            </IonButton>
          </IonButtons>
          <IonTitle>{TEXT.services}</IonTitle>
          <IonButtons slot="end">
            <IonButton strong={true} onClick={save}>
              {TEXT.save}
            </IonButton>
          </IonButtons>
        </IonToolbar>
        <IonToolbar>
          <IonSearchbar
            debounce={500}
            placeholder={TEXT.search}
            onIonChange={(e) => setSearchValue(e.detail.value)}
          ></IonSearchbar>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <IonList>
          {Object.entries(filteredList).map(([key, value]) => {
            return (
              <IonItem key={key}>
                <IonCheckbox
                  checked={value?.selected}
                  onIonChange={(e) => {
                    const newServiceList = Object.assign({}, servicesList);
                    newServiceList[key].selected = e.target.checked;
                    setServicesList(newServiceList);
                  }}
                  slot="start"
                ></IonCheckbox>
                <IonLabel>
                  <IonText color="primary">{value?.name}</IonText>
                </IonLabel>
                {value?.selected && (
                  <CurrencyInput
                    inputmode="numeric"
                    class="ion-text-end"
                    value={currencyFormat(value.cost)}
                    placeholder={TEXT.zeroMoney}
                    onKeyDown={(e) => {
                      e.preventDefault();
                      const newServiceList = Object.assign({}, servicesList);
                      newServiceList[key].cost = +updateNumberByKeyandPrevious(
                        e.key.toString(),
                        value.cost ? value.cost.toString() : "0"
                      );
                      setServicesList(newServiceList);
                    }}
                    onIonChange={(e) => {
                      e.preventDefault();
                    }}
                  ></CurrencyInput>
                )}
              </IonItem>
            );
          })}
          {Object.entries(filteredList).length === 0 &&
            searchValue !== undefined && (
              <>
                <ItemNotFound />
                <IonItem>
                  <IonButton
                    slot="end"
                    onClick={() => {
                      addToList(searchValue);
                    }}
                  >
                    {`${TEXT.addItem} "${searchValue}"`}
                  </IonButton>
                </IonItem>
              </>
            )}
        </IonList>
      </IonContent>
    </IonPage>
  );
};

export default ServiceAddModal;
