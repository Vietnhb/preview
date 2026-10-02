import { useEffect, useState } from "react";
import axios from "axios";
import { getMe } from "../features/account/api/userApi";
import { useSessionStore } from "../shared/auth/sessionStore";
import { clearToken, getToken } from "../shared/lib/token";
import { isTokenExpired } from "../shared/lib/jwt";

export function useSessionBootstrap() {
  const setUser = useSessionStore(state => state.setUser);
  const [authReady, setAuthReady] = useState(false);
  useEffect(() => {
    const token = getToken();
    if (!token || isTokenExpired(token)) {
      if (token) clearToken();
      setUser(null);
      void Promise.resolve().then(() => setAuthReady(true));
      return;
    }
    let active = true;
    void getMe()
      .then((user) => {
        if (!active) return;
        if (getToken() === token) setUser(user);
        setAuthReady(true);
      })
      .catch((error) => {
        if (!active) return;
        if (getToken() === token && axios.isAxiosError(error)
          && (error.response?.status === 401 || error.response?.status === 403)) {
          clearToken();
          setUser(null);
        }
        setAuthReady(true);
      });
    return () => {
      active = false;
    };
  }, [setUser]);

  return authReady;
}