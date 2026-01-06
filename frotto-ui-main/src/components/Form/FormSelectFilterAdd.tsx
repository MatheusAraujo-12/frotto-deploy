import {
  IonButton,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonModal,
  IonSearchbar,
  IonText,
  IonToolbar,
} from "@ionic/react";
import { TEXT } from "../../constants/texts";
import FormInputLabel from "./FormInputLabel";
import { caretDown } from "ionicons/icons";
import { useCallback, useMemo, useState } from "react";

import ItemNotFound from "../List/ItemNotFound";
import { filterListString } from "../../services/filterList";
import {
  addOneToArray,
  getArrays,
} from "../../services/localStorage/localstorage";
import styled from "styled-components";
import FormItemWrapper from "./FormItemWrapper";

const MyIonModal = styled(IonModal)`
  background-color: var(--app-modal-backdrop);
  --height: 70%;
`;

const MyIonIcon = styled(IonIcon)`
  font-size: 15px;
  color: var(--ion-color-medium);
`;

export interface SelectFilterAddProps {
  label: string;
  header?: string;
  formCallBack: Function;
  required?: boolean;
  initialValue?: string | undefined;
  options: string[];
  storageToken?: string;
  errorsObj?: Object;
  errorName?: string;
}

const FormSelectFilterAdd: React.FC<SelectFilterAddProps> = ({
  label,
  header,
  formCallBack,
  required,
  initialValue,
  options,
  storageToken,
  errorsObj,
  errorName,
}) => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState<string | undefined>(undefined);
  const [selectValue, setSelectValue] = useState<string | undefined>(
    initialValue
  );
  const [storageOptions, setStorageOptions] = useState<string[]>([]);

  const loadStorageOptions = () => {
    if (storageToken) {
      setStorageOptions(getArrays(storageToken));
    }
  };

  const filteredList = useMemo(() => {
    const finalOptions = [...options, ...storageOptions];
    return filterListString(finalOptions, searchValue);
  }, [options, searchValue, storageOptions]);

  const addToList = useCallback(
    (newItem: string | undefined) => {
      if (storageToken && newItem && newItem !== "") {
        addOneToArray(newItem, storageToken);
        loadStorageOptions();
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [storageToken]
  );

  return (
    <FormItemWrapper errorsObj={errorsObj} errorName={errorName}>
      <IonItem
        button
        detail={false}
        id="open-modal"
        onClick={() => {
          setSearchValue(undefined);
          loadStorageOptions();
          setIsModalOpen(true);
        }}
      >
        <FormInputLabel name={label} header={header} required={required} />
        <IonButton
          slot="end"
          fill="clear"
          class="ion-no-padding ion-no-marging"
          color={selectValue ? "primary" : "medium"}
        >
          {selectValue && <>{selectValue}</>}
          {!selectValue && <p>{TEXT.select}</p>}
          <MyIonIcon slot="end" icon={caretDown} />
        </IonButton>
      </IonItem>
      <MyIonModal
        isOpen={isModalOpen}
        onDidDismiss={() => {
          setIsModalOpen(false);
        }}
      >
        <IonHeader>
          <IonToolbar>
            <IonSearchbar
              debounce={500}
              placeholder={TEXT.searchAdd}
              onIonChange={(e) => setSearchValue(e.detail.value)}
            ></IonSearchbar>
          </IonToolbar>
        </IonHeader>
        <IonContent>
          <IonList>
            {filteredList.map((item: string, index) => {
              return (
                <IonItem
                  button
                  key={index}
                  onClick={() => {
                    setIsModalOpen(false);
                    setSelectValue(item);
                    formCallBack(item);
                  }}
                >
                  <IonLabel>
                    <IonText color="primary">{item}</IonText>
                  </IonLabel>
                </IonItem>
              );
            })}

            {filteredList.length === 0 && (
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
      </MyIonModal>
    </FormItemWrapper>
  );
};

export default FormSelectFilterAdd;
