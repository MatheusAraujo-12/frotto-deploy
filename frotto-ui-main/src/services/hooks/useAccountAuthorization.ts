import { useEffect, useState } from "react";
import accountService from "../accountService";
import { accountIsAdmin } from "../authorization";
import { getToken, subscribeToTokenChanges } from "../localStorage/localstorage";

interface AccountAuthorizationState {
  isAdmin: boolean;
  isLoading: boolean;
}

export const useAccountAuthorization = (): AccountAuthorizationState => {
  const [state, setState] = useState<AccountAuthorizationState>({
    isAdmin: false,
    isLoading: Boolean(getToken()),
  });

  useEffect(() => {
    let isMounted = true;
    let requestId = 0;

    const loadCurrentAccount = async () => {
      const token = getToken();
      const currentRequestId = ++requestId;

      setState({ isAdmin: false, isLoading: Boolean(token) });
      if (!token) {
        return;
      }

      try {
        const account = await accountService.getAccount();
        if (isMounted && currentRequestId === requestId && getToken() === token) {
          setState({ isAdmin: accountIsAdmin(account), isLoading: false });
        }
      } catch (_error) {
        if (isMounted && currentRequestId === requestId && getToken() === token) {
          setState({ isAdmin: false, isLoading: false });
        }
      }
    };

    const unsubscribe = subscribeToTokenChanges(() => {
      void loadCurrentAccount();
    });
    void loadCurrentAccount();

    return () => {
      isMounted = false;
      requestId += 1;
      unsubscribe();
    };
  }, []);

  return state;
};
