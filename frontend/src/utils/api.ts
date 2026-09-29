import { apiUrl } from '../config';

type Options = RequestInit & { raw?: boolean; headers?: HeadersInit };
function api<T>(url: string, options: Options): Promise<T> {
  return fetch(url, { headers: authHeader(false), ...options }).then(
    async (response) => {
      if (!response.ok) {
        // An error with no JSON body (a proxy error page, an empty 403) must
        // still reject with something getErrorMessage can read, not with
        // "Unexpected end of JSON input".
        const body = await response
          .json()
          .catch(() => ({ status: response.status }));
        throw new Error(JSON.stringify(body));
      }
      if (options?.raw) return response as unknown as Promise<T>;
      return response.json() as Promise<T>;
    }
  );
}

function get<T>(url, options?: Options) {
  return api<T>(apiUrl + url, options);
}

function post<T>(url, data, options?: Options, isNotJson?: boolean) {
  return api<T>(apiUrl + url, {
    ...options,
    method: 'POST',
    body: isNotJson ? data : JSON.stringify(data)
  });
}

function patch<T>(url, data, options?: Options) {
  return api<T>(apiUrl + url, {
    ...options,
    method: 'PATCH',
    body: JSON.stringify(data)
  });
}

function deletes<T>(url, options?: Options) {
  return api<T>(apiUrl + url, { ...options, method: 'DELETE' });
}

export function authHeader(publicRoute: boolean): HeadersInit {
  // return authorization header with jwt token
  let accessToken = localStorage.getItem('accessToken');

  if (!publicRoute && accessToken) {
    return {
      Authorization: 'Bearer ' + accessToken,
      Accept: 'application/json',
      'Content-Type': 'application/json'
    };
  } else {
    return {
      Accept: 'application/json',
      'Content-Type': 'application/json'
    };
  }
}

export const getErrorMessage = (
  error: any,
  defaultMessage?: string
): string => {
  try {
    const parsed = JSON.parse(error.message);
    // A parsed body without a message is raw JSON — not something to show.
    return parsed?.message ?? defaultMessage ?? error.message;
  } catch {
    return error.message ?? defaultMessage;
  }
};

export default { get, patch, post, deletes };
