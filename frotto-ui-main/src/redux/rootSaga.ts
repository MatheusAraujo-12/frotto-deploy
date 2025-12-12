/* eslint-disable no-console */
import { all, spawn } from "redux-saga/effects";

export default function* rootSaga() {
  const effects: any[] = [];

  yield all(
    effects.map((effect) => {
      let loops = 0;

      return spawn(function* genericSaga() {
        while (true) {
          if (loops > 10) {
            // Even in a big session, if a saga restarts more than 10 times,
            // we are probably in a eternal loop. Breaking out and killing app.
            if (process.env.NODE_ENV === "development") {
              console.error(effect);
            }

            throw new Error("Eternal Saga loop detected.");
          }

          loops += 1;

          try {
            // instantiate our saga.
            yield effect;

            // if it completes successfuly (call() returns) we terminate the thread.
            break;
          } catch (error) {
            // if it errors (call() errors), re-instantiate the saga
            if (process.env.NODE_ENV === "development") {
              console.log(error);
            }
            // TODO: How should we handle/report this errors?
          }
        }
      });
    })
  );
}
