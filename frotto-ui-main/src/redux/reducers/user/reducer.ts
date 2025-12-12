import { createReducer } from "@reduxjs/toolkit";

import {
  FETCH_USER_DATA,
  FETCH_USER_DATA_SUCCESS,
  FETCH_USER_DATA_ERROR,
  REGISTER_USER,
  REGISTER_USER_SUCCESS,
  REGISTER_USER_ERROR,
  AUTH_USER,
  AUTH_USER_SUCCESS,
  AUTH_USER_ERROR,
} from "./actions";

export const initialState = {
  id: null,
  name: null,
  email: null,
  // loading varibles
  loading: false,
  error: null,
};

export default createReducer(initialState, {
  [FETCH_USER_DATA]: (state) => {
    state.loading = true;
  },
  [FETCH_USER_DATA_SUCCESS]: (state, { payload }) => ({
    ...state,
    ...payload,
    loading: false,
    error: null,
  }),
  [FETCH_USER_DATA_ERROR]: (state, { payload }) => {
    state.loading = false;
    state.error = payload;
  },
  [REGISTER_USER]: (state) => {
    state.loading = true;
  },
  [REGISTER_USER_SUCCESS]: (state) => {
    state.loading = false;
  },
  [REGISTER_USER_ERROR]: (state, { payload }) => {
    state.loading = false;
    state.error = payload;
  },
  [AUTH_USER]: (state) => {
    state.loading = true;
  },
  [AUTH_USER_SUCCESS]: (state) => {
    state.loading = false;
  },
  [AUTH_USER_ERROR]: (state, { payload }) => {
    state.loading = false;
    state.error = payload;
  },
});
