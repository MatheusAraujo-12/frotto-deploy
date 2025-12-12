import { all, call, put, takeLatest } from "redux-saga/effects";

import axios from "../../../services/axios/axios";
import endpoints from "../../../constants/endpoints";
import {
  fetchUserDataSuccess,
  fetchUserDataError,
  FETCH_USER_DATA,
  REGISTER_USER,
  AUTH_USER,
  registerUserSuccess,
  registerUserError,
  authUserSuccess,
  authUserError,
} from "./actions";
import { setToken } from "../../../services/localStorage/localstorage";

export function* fetchUserDataSaga(action: any) {
  try {
    const { payload: id } = action;
    const { data } = yield call(
      axios.get,
      endpoints.USER({
        pathVariables: {
          id: id,
        },
      })
    );

    yield put(fetchUserDataSuccess(data));
  } catch (error) {
    yield put(fetchUserDataError(error));
  }
}

export function* registerUserDataSaga(action: any) {
  try {
    const { payload } = action;
    // {
    //   "firstName": "Fernando1",
    //   "email":"fernando1@gmail.com",
    //   "password": "123!@#"
    //   }
    yield call(axios.post, endpoints.REGISTER(), payload);
    yield put(registerUserSuccess());
  } catch (error) {
    yield put(registerUserError(error));
  }
}

export function* authUserDataSaga(action: any) {
  try {
    const { payload } = action;
    // {
    //   "username": "fernando1@gmail.com",
    //   "rememberMe":true,
    //   "password": "123!@#"
    //   }

    const { data } = yield call(axios.post, endpoints.AUTH(), payload);

    setToken("Bearer " + data["id_token"]);

    yield put(authUserSuccess());
  } catch (error) {
    yield put(authUserError(error));
  }
}

function* postsSaga() {
  yield all([
    takeLatest(FETCH_USER_DATA, fetchUserDataSaga),
    takeLatest(REGISTER_USER, registerUserDataSaga),
    takeLatest(AUTH_USER, authUserDataSaga),
  ]);
}

export default postsSaga;
