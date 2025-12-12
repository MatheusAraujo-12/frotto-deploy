import { createAction } from "@reduxjs/toolkit";

export const reducerName = "user";

export const FETCH_USER_DATA = `@${reducerName}/FETCH_USER_DATA`;
export const FETCH_USER_DATA_SUCCESS = `@${reducerName}/FETCH_USER_DATA_SUCCESS`;
export const FETCH_USER_DATA_ERROR = `@${reducerName}/FETCH_USER_DATA_ERROR`;

export const REGISTER_USER = `@${reducerName}/REGISTER_USER`;
export const REGISTER_USER_SUCCESS = `@${reducerName}/REGISTER_USER_SUCCESS`;
export const REGISTER_USER_ERROR = `@${reducerName}/REGISTER_USER_ERROR`;

export const AUTH_USER = `@${reducerName}/AUTH_USER`;
export const AUTH_USER_SUCCESS = `@${reducerName}/AUTH_USER_SUCCESS`;
export const AUTH_USER_ERROR = `@${reducerName}/AUTH_USER_ERROR`;

export const fetchUserData = createAction(FETCH_USER_DATA);
export const fetchUserDataSuccess = createAction(
  FETCH_USER_DATA_SUCCESS,
  (payload) => ({ payload })
);
export const fetchUserDataError = createAction(
  FETCH_USER_DATA_ERROR,
  (payload) => ({ payload })
);

export const registerUser = createAction(REGISTER_USER, (payload) => ({
  payload,
}));
export const registerUserSuccess = createAction(REGISTER_USER_SUCCESS);
export const registerUserError = createAction(
  REGISTER_USER_ERROR,
  (payload) => ({ payload })
);

export const authUser = createAction(AUTH_USER, (payload) => ({ payload }));
export const authUserSuccess = createAction(AUTH_USER_SUCCESS);
export const authUserError = createAction(AUTH_USER_ERROR, (payload) => ({
  payload,
}));
