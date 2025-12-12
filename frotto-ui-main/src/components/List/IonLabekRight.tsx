import { IonLabel, IonNote, IonText } from "@ionic/react";

import styled from "styled-components";

export const IonLabelLeft = styled(IonLabel).attrs(() => ({
  slot: "start",
}))`
  margin-right: -10px;
  align-self: start;
  text-align: start;
`;

export const IonLabekRight = styled(IonLabel).attrs(() => ({
  slot: "end",
}))`
  margin-left: 0px;
  align-self: start;
  text-align: end;
`;

export const IonLabekRightCarList = styled.div.attrs(() => ({
  slot: "end",
}))`
  position: absolute;
  top: 10px;
  inset-inline-end: 10px;
  font-size: 0.8rem;
  display: flex;
  align-items: center;
`;

export const IonLabelCarList = styled(IonLabel).attrs(() => ({}))`
  max-width: calc(100% - 30px);
`;

export const IonNoteCarList = styled(IonNote).attrs(() => ({}))`
  font-size: 0.9rem;
`;
export const IonSubTextCarList = styled(IonText).attrs(() => ({}))`
  font-size: 0.9rem;
`;