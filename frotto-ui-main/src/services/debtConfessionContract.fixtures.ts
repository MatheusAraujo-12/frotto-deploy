/**
 * Real responses of the backend (commit 770d4bc) for Pendências -> Confissão de Dívida, captured from a local run:
 * João (driver 1) with debts born in the Onix (primary), the HB20 (reserve) and the Argo (after a definitive
 * transfer, partially paid), Maria (driver 2) later on the Onix, a legacy row without debtor, another account.
 */

export const PREVIEW_MULTI_CAR = {
  "driverCarId": null,
  "contractNumber": null,
  "contractStartDate": null,
  "contractEndDate": null,
  "contractConcluded": null,
  "driverId": 1,
  "driverName": "João Silva",
  "driverCpf": "11111111111",
  "carId": null,
  "carPlate": null,
  "carModel": null,
  "origemDaDivida": "Pendências em aberto registradas em nome do motorista, referentes aos contratos e veículos discriminados nos itens abaixo.",
  "items": [
    {
      "pendencyId": 1,
      "name": "Multa",
      "date": "2026-08-20",
      "note": "AIT 123",
      "status": "OPEN",
      "cost": 200,
      "paidAmount": 0,
      "remainingAmount": 200,
      "typeId": 7,
      "typeNameSnapshot": "Outros",
      "descricaoItem": "Multa (20/08/2026). AIT 123",
      "valorItem": 200,
      "originDriverCarId": 1,
      "originContractNumber": null,
      "originCarId": 1,
      "originCarPlate": "ONX1A11",
      "originCarModel": "Onix"
    },
    {
      "pendencyId": 2,
      "name": "Danos/Avarias",
      "date": "2026-09-05",
      "note": null,
      "status": "OPEN",
      "cost": 350,
      "paidAmount": 0,
      "remainingAmount": 350,
      "typeId": 5,
      "typeNameSnapshot": "Danos/Avarias",
      "descricaoItem": "Danos/Avarias (05/09/2026)",
      "valorItem": 350,
      "originDriverCarId": 2,
      "originContractNumber": null,
      "originCarId": 2,
      "originCarPlate": "HBV2B22",
      "originCarModel": "HB20"
    },
    {
      "pendencyId": 3,
      "name": "Multa",
      "date": "2026-09-25",
      "note": null,
      "status": "PARTIALLY_PAID",
      "cost": 1000,
      "paidAmount": 400,
      "remainingAmount": 600,
      "typeId": 7,
      "typeNameSnapshot": "Outros",
      "descricaoItem": "Multa (25/09/2026) - saldo remanescente; valor original R$ 1.000,00, já pago R$ 400,00",
      "valorItem": 600,
      "originDriverCarId": 3,
      "originContractNumber": null,
      "originCarId": 3,
      "originCarPlate": "ARG3C33",
      "originCarModel": "Argo"
    }
  ],
  "valorTotal": 1150
};

export const PREVIEW_SINGLE = {
  "driverCarId": 1,
  "contractNumber": null,
  "contractStartDate": "2026-08-01",
  "contractEndDate": "2026-09-20",
  "contractConcluded": true,
  "driverId": 1,
  "driverName": "João Silva",
  "driverCpf": "11111111111",
  "carId": 1,
  "carPlate": "ONX1A11",
  "carModel": "Onix",
  "origemDaDivida": "Pendências em aberto registradas no contrato iniciado em 01/08/2026, veículo placa ONX1A11.",
  "items": [
    {
      "pendencyId": 1,
      "name": "Multa",
      "date": "2026-08-20",
      "note": "AIT 123",
      "status": "OPEN",
      "cost": 200,
      "paidAmount": 0,
      "remainingAmount": 200,
      "typeId": 7,
      "typeNameSnapshot": "Outros",
      "descricaoItem": "Multa (20/08/2026). AIT 123",
      "valorItem": 200,
      "originDriverCarId": 1,
      "originContractNumber": null,
      "originCarId": 1,
      "originCarPlate": "ONX1A11",
      "originCarModel": "Onix"
    }
  ],
  "valorTotal": 200
};

export const JOAO_PENDENCIES = [
  {
    "id": 4,
    "name": "Combustível",
    "cost": 50,
    "date": "2026-09-26",
    "note": null,
    "status": "PAID",
    "paidAt": "2026-10-06T14:05:14.916054Z",
    "paidAmount": 50,
    "remainingAmount": 0,
    "paymentMethod": null,
    "driverCarId": 3,
    "debtorDriverId": 1
  },
  {
    "id": 3,
    "name": "Multa",
    "cost": 1000,
    "date": "2026-09-25",
    "note": null,
    "status": "PARTIALLY_PAID",
    "paidAt": null,
    "paidAmount": 400,
    "remainingAmount": 600,
    "paymentMethod": null,
    "driverCarId": 3,
    "debtorDriverId": 1
  },
  {
    "id": 2,
    "name": "Danos/Avarias",
    "cost": 350,
    "date": "2026-09-05",
    "note": null,
    "status": "OPEN",
    "paidAt": null,
    "paidAmount": 0,
    "remainingAmount": 350,
    "paymentMethod": null,
    "driverCarId": 2,
    "debtorDriverId": 1
  },
  {
    "id": 1,
    "name": "Multa",
    "cost": 200,
    "date": "2026-08-20",
    "note": "AIT 123",
    "status": "OPEN",
    "paidAt": null,
    "paidAmount": 0,
    "remainingAmount": 200,
    "paymentMethod": null,
    "driverCarId": 1,
    "debtorDriverId": 1
  }
];

export const LEGACY_WITHOUT_DEBTOR = {
  "id": 6,
  "name": "Legado",
  "cost": 90,
  "date": "2026-01-10",
  "note": null,
  "status": "OPEN",
  "paidAt": null,
  "paidAmount": 0,
  "remainingAmount": 90,
  "paymentMethod": null,
  "driverCarId": 4,
  "debtorDriverId": null
};

export const ERROR_DIFFERENT_DEBTORS_400 = {
  "entityName": "pendency",
  "errorKey": "pendenciesdifferentdebtors",
  "type": "https://www.jhipster.tech/problem/problem-with-message",
  "title": "Todas as pendências da confissão devem ser do mesmo motorista.",
  "status": 400,
  "message": "error.pendenciesdifferentdebtors",
  "params": "pendency"
};

export const ERROR_NOT_OPEN_400 = {
  "entityName": "pendency",
  "errorKey": "pendencynotopen",
  "type": "https://www.jhipster.tech/problem/problem-with-message",
  "title": "Somente pendências com saldo em aberto podem entrar na confissão.",
  "status": 400,
  "message": "error.pendencynotopen",
  "params": "pendency"
};

export const ERROR_WITHOUT_DEBTOR_400 = {
  "entityName": "pendency",
  "errorKey": "pendencywithoutdebtor",
  "type": "https://www.jhipster.tech/problem/problem-with-message",
  "title": "Pendência sem motorista devedor.",
  "status": 400,
  "message": "error.pendencywithoutdebtor",
  "params": "pendency"
};

export const ERROR_FOREIGN_404 = {
  "type": "https://www.jhipster.tech/problem/problem-with-message",
  "title": "Not Found",
  "status": 404,
  "detail": "404 NOT_FOUND \"Pendency not found for current user\"",
  "path": "/api/pendencies/confissao-divida/preview",
  "message": "error.http.404"
};

export const ERROR_OUTDATED_409 = {
  "errorKey": "confessionpendencieschanged",
  "type": "https://www.jhipster.tech/problem/problem-with-message",
  "title": "As pendências da confissão foram alteradas. Atualize a confissão antes de continuar.",
  "status": 409,
  "message": "error.confessionpendencieschanged",
  "params": "document"
};

/** GET /api/documents/{id} after create + finalize (attachments and urls omitted). */
export const STORED_CONFESSION = {
  "id": 1,
  "type": "CONFISSAO_DIVIDA",
  "status": "FINAL",
  "driverId": 1,
  "driverName": "João Silva",
  "driverCpf": "11111111111",
  "carId": null,
  "carPlate": null,
  "carModel": null,
  "payload": {
    "formaPagamento": "PARCELADO",
    "parcelasQtd": 3,
    "valorParcela": 383.33,
    "vencimentoInicial": "2026-11-10",
    "origemDaDivida": "Pendências em aberto registradas em nome do motorista, referentes aos contratos e veículos discriminados nos itens abaixo.",
    "driverName": "João Silva",
    "driverCpf": "11111111111",
    "carPlate": null,
    "carModel": null,
    "itensDaDivida": [
      {
        "typeId": 7,
        "typeNameSnapshot": "Outros",
        "descricaoItem": "Multa (20/08/2026). AIT 123",
        "valorItem": 200,
        "sourcePendencyId": 1
      },
      {
        "typeId": 5,
        "typeNameSnapshot": "Danos/Avarias",
        "descricaoItem": "Danos/Avarias (05/09/2026)",
        "valorItem": 350,
        "sourcePendencyId": 2
      },
      {
        "typeId": 7,
        "typeNameSnapshot": "Outros",
        "descricaoItem": "Multa (25/09/2026) - saldo remanescente; valor original R$ 1.000,00, já pago R$ 400,00",
        "valorItem": 600,
        "sourcePendencyId": 3
      }
    ],
    "valorTotal": 1150,
    "origem": {
      "tipo": "PENDENCIAS",
      "versao": 2,
      "driverId": 1,
      "pendencyIds": [
        1,
        2,
        3
      ],
      "driverCarId": null,
      "carId": null,
      "contractNumber": null,
      "contractStartDate": null,
      "contractEndDate": null,
      "contractConcluded": null,
      "driverName": "João Silva",
      "driverCpf": "11111111111",
      "carPlate": null,
      "carModel": null,
      "pendencias": [
        {
          "id": 1,
          "name": "Multa",
          "date": "2026-08-20",
          "note": "AIT 123",
          "status": "OPEN",
          "cost": 200,
          "paidAmount": 0,
          "remainingAmount": 200,
          "driverCarId": 1,
          "contractNumber": null,
          "carId": 1,
          "carPlate": "ONX1A11",
          "carModel": "Onix"
        },
        {
          "id": 2,
          "name": "Danos/Avarias",
          "date": "2026-09-05",
          "note": null,
          "status": "OPEN",
          "cost": 350,
          "paidAmount": 0,
          "remainingAmount": 350,
          "driverCarId": 2,
          "contractNumber": null,
          "carId": 2,
          "carPlate": "HBV2B22",
          "carModel": "HB20"
        },
        {
          "id": 3,
          "name": "Multa",
          "date": "2026-09-25",
          "note": null,
          "status": "PARTIALLY_PAID",
          "cost": 1000,
          "paidAmount": 400,
          "remainingAmount": 600,
          "driverCarId": 3,
          "contractNumber": null,
          "carId": 3,
          "carPlate": "ARG3C33",
          "carModel": "Argo"
        }
      ],
      "valorTotal": 1150,
      "snapshotEm": "2026-10-06T14:05:15.323278700Z"
    }
  }
};
