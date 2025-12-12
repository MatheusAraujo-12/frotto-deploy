import moment from "moment/";
moment.defineLocale("pt-br", {
  months:
    "Janeiro_Fevereiro_Março_Abril_Maio_Junho_Julho_Agosto_Setembro_Outubro_Novembro_Dezembro".split(
      "_"
    ),
  monthsShort: "jan_fev_mar_abr_mai_jun_jul_ago_set_out_nov_dez".split("_"),
  weekdays:
    "domingo_segunda-feira_terça-feira_quarta-feira_quinta-feira_sexta-feira_sábado".split(
      "_"
    ),
  weekdaysShort: "dom_seg_ter_qua_qui_sex_sáb".split("_"),
  weekdaysMin: "dom_2ª_3ª_4ª_5ª_6ª_sáb".split("_"),
});
moment.locale("pt-br");

export const formatDateView = (date: string | undefined): String => {
  if (date) {
    return moment(new Date(date).toUTCString()).utc().format("DD/MM/YYYY");
  }
  return "";
};

export const stringDateToDB = (dateStg: string): string => {
  const date = new Date(dateStg);
  return moment(date.toUTCString()).utc().format("YYYY-MM-DD");
};

export const stringDateToDay = (dateStg: string): string => {
  return moment(new Date(dateStg).toUTCString()).utc().format("DD");
};

export const stringDateToMonthYear = (dateStg: string): string => {
  return moment(new Date(dateStg).toUTCString()).utc().format("MMMM YYYY");
};

export const formatDateViewMMYYYY = (date: string | undefined): String => {
  if (date) {
    return moment(new Date(date).toUTCString()).utc().format("MM/YYYY");
  }
  return "";
};
